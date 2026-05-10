package com.notebook.lumen.identity.sso.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.admin.AdminRbacProperties;
import com.notebook.lumen.identity.admin.AdminRbacService;
import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.auth.application.AuthService;
import com.notebook.lumen.identity.shared.exception.SsoException;
import com.notebook.lumen.identity.shared.security.EmailNormalizer;
import com.notebook.lumen.identity.sso.SsoProperties;
import com.notebook.lumen.identity.sso.domain.ExternalIdentity;
import com.notebook.lumen.identity.sso.infrastructure.ExternalIdentityRepository;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserSource;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SsoService {
  private static final String STATE_PREFIX = "sso:state:";

  private final SsoProperties properties;
  private final AdminRbacProperties adminRbacProperties;
  private final AdminRbacService adminRbacService;
  private final AuthService authService;
  private final UserRepository userRepository;
  private final ExternalIdentityRepository externalIdentityRepository;
  private final PasswordEncoder passwordEncoder;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final OidcClient oidcClient;

  public SsoService(
      SsoProperties properties,
      AdminRbacProperties adminRbacProperties,
      AdminRbacService adminRbacService,
      AuthService authService,
      UserRepository userRepository,
      ExternalIdentityRepository externalIdentityRepository,
      PasswordEncoder passwordEncoder,
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      OidcClient oidcClient) {
    this.properties = properties;
    this.adminRbacProperties = adminRbacProperties;
    this.adminRbacService = adminRbacService;
    this.authService = authService;
    this.userRepository = userRepository;
    this.externalIdentityRepository = externalIdentityRepository;
    this.passwordEncoder = passwordEncoder;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.oidcClient = oidcClient;
  }

  public List<SsoProviderView> listProviders() {
    ensureEnabled();
    return properties.enabledProviders().stream()
        .map(
            provider ->
                new SsoProviderView(provider.registrationId(), "Continue with " + provider.registrationId()))
        .toList();
  }

  public URI buildAuthorizeUri(
      String providerId, String returnUrl, HttpServletRequest httpServletRequest) {
    ensureEnabled();
    SsoProperties.Provider provider = provider(providerId);
    OidcProviderMetadata metadata = oidcClient.discover(provider);
    String state = UUID.randomUUID().toString();
    String nonce = UUID.randomUUID().toString();
    String sanitizedReturnUrl = sanitizeReturnUrl(returnUrl);
    String redirectUri = callbackUrl(httpServletRequest, provider.registrationId());

    saveState(
        state,
        new StatePayload(
            provider.registrationId(), nonce, sanitizedReturnUrl, metadata.issuer(), Instant.now().toString()));
    return oidcClient.buildAuthorizeUri(metadata, provider, state, nonce, redirectUri);
  }

  @Transactional
  public SsoCallbackResult handleCallback(
      String providerId, String code, String state, HttpServletRequest request) {
    ensureEnabled();
    if (state == null || state.isBlank()) {
      throw new SsoException("SSO_STATE_INVALID", HttpStatus.UNAUTHORIZED, "SSO state is invalid");
    }
    StatePayload payload = readState(state);
    if (payload == null) {
      throw new SsoException("SSO_STATE_EXPIRED", HttpStatus.UNAUTHORIZED, "SSO state expired");
    }
    redisTemplate.delete(STATE_PREFIX + state);
    SsoProperties.Provider provider = provider(providerId);
    if (!provider.registrationId().equals(payload.provider())) {
      throw new SsoException("SSO_STATE_INVALID", HttpStatus.UNAUTHORIZED, "SSO state is invalid");
    }

    OidcProviderMetadata metadata = oidcClient.discover(provider);
    String redirectUri = callbackUrl(request, provider.registrationId());
    OidcIdTokenProfile profile = oidcClient.exchangeAndValidate(provider, metadata, code, redirectUri);
    if (!payload.nonce().equals(profile.nonce())) {
      throw new SsoException("SSO_STATE_INVALID", HttpStatus.UNAUTHORIZED, "SSO nonce mismatch");
    }
    validateExternalProfile(provider, profile);

    User user = upsertUser(provider, profile);
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("auth_provider", provider.registrationId());
    claims.put("sso_groups", profile.groups());
    if (adminRbacProperties.enabled()) {
      List<String> mapped = adminRbacService.mapIdpGroupsToRoles(provider, profile.groups());
      if (!mapped.isEmpty()) {
        claims.put("platform_roles", mapped);
      }
    } else if (isPlatformAdmin(provider, profile.groups())) {
      claims.put("platform_roles", List.of(PlatformAdminRbacConstants.ROLE_PLATFORM_ADMIN));
    }
    claims.putAll(mfaClaimsFromIdp(profile));

    AuthResponse authResponse = authService.issueTokensForUser(user, request, claims);
    return new SsoCallbackResult(authResponse, payload.returnUrl());
  }

  private Map<String, Object> mfaClaimsFromIdp(OidcIdTokenProfile profile) {
    if (!properties.trustIdpMfa()) {
      return Map.of("mfa_verified", false, "amr", List.of("sso"), "mfa_verified_at", "");
    }
    Set<String> requiredAmr = properties.requiredAmrSet();
    boolean amrSatisfied = requiredAmr.isEmpty() || profile.amr().stream().map(v -> v.toLowerCase(Locale.ROOT)).anyMatch(requiredAmr::contains);
    String requiredAcr = properties.effectiveRequiredAcr();
    boolean acrSatisfied = requiredAcr.isBlank() || requiredAcr.equals(profile.acr());
    if (amrSatisfied && acrSatisfied) {
      return Map.of(
          "mfa_verified", true,
          "amr", profile.amr().isEmpty() ? List.of("sso") : profile.amr(),
          "mfa_verified_at", Instant.now().toString());
    }
    return Map.of("mfa_verified", false, "amr", List.of("sso"), "mfa_verified_at", "");
  }

  private User upsertUser(SsoProperties.Provider provider, OidcIdTokenProfile profile) {
    Optional<ExternalIdentity> existing =
        externalIdentityRepository.findByProviderAndSubject(provider.registrationId(), profile.subject());
    if (existing.isPresent()) {
      ExternalIdentity identity = existing.get();
      identity.markLogin(Instant.now(), profile.email(), profile.emailVerified(), profile.sanitizedClaims());
      externalIdentityRepository.save(identity);
      return identity.getUser();
    }

    String normalizedEmail = EmailNormalizer.normalize(profile.email());
    User user =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseGet(() -> createUserFromSso(normalizedEmail));
    ExternalIdentity identity =
        new ExternalIdentity(
            UUID.randomUUID(),
            user,
            provider.registrationId(),
            profile.subject(),
            normalizedEmail,
            true,
            profile.sanitizedClaims(),
            Instant.now(),
            Instant.now());
    externalIdentityRepository.save(identity);
    return user;
  }

  private User createUserFromSso(String normalizedEmail) {
    Instant now = Instant.now();
    User created =
        new User(
            UUID.randomUUID(),
            normalizedEmail,
            normalizedEmail,
            null,
            passwordEncoder.encode(UUID.randomUUID().toString()),
            UserStatus.ACTIVE,
            now,
            now,
            now,
            now,
            now,
            null,
            UserSource.SSO,
            null,
            null);
    return userRepository.save(created);
  }

  private void validateExternalProfile(SsoProperties.Provider provider, OidcIdTokenProfile profile) {
    if (profile.subject() == null || profile.subject().isBlank()) {
      throw new SsoException("SSO_ID_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "Missing OIDC subject");
    }
    if (profile.email() == null || profile.email().isBlank()) {
      throw new SsoException("SSO_ID_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "Missing OIDC email");
    }
    if (!profile.emailVerified()) {
      throw new SsoException(
          "SSO_EMAIL_NOT_VERIFIED", HttpStatus.UNAUTHORIZED, "SSO email is not verified");
    }
    String domain = profile.email().substring(profile.email().lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
    Set<String> allowedDomains = provider.allowedDomainSet();
    if (!allowedDomains.isEmpty() && !allowedDomains.contains(domain)) {
      throw new SsoException(
          "SSO_DOMAIN_NOT_ALLOWED", HttpStatus.FORBIDDEN, "SSO email domain is not allowed");
    }
  }

  private boolean isPlatformAdmin(SsoProperties.Provider provider, List<String> groups) {
    Set<String> adminGroups = provider.adminGroupSet();
    if (adminGroups.isEmpty()) {
      return false;
    }
    return groups.stream()
        .map(value -> value.toLowerCase(Locale.ROOT))
        .anyMatch(adminGroups::contains);
  }

  private String callbackUrl(HttpServletRequest request, String providerId) {
    String base = request.getRequestURL().toString();
    int callbackIndex = base.indexOf("/callback");
    if (callbackIndex > 0) {
      return base.substring(0, callbackIndex + "/callback".length());
    }
    return request.getScheme()
        + "://"
        + request.getServerName()
        + ":"
        + request.getServerPort()
        + "/auth/sso/"
        + providerId
        + "/callback";
  }

  private String sanitizeReturnUrl(String returnUrl) {
    if (returnUrl == null || returnUrl.isBlank()) {
      return "/app";
    }
    if (!returnUrl.startsWith("/")) {
      return "/app";
    }
    if (returnUrl.startsWith("//")) {
      return "/app";
    }
    return returnUrl;
  }

  private void saveState(String state, StatePayload payload) {
    try {
      redisTemplate
          .opsForValue()
          .set(
              STATE_PREFIX + state,
              objectMapper.writeValueAsString(payload),
              Duration.ofSeconds(properties.effectiveStateTtlSeconds()));
    } catch (JsonProcessingException ex) {
      throw new SsoException("SSO_STATE_INVALID", HttpStatus.UNAUTHORIZED, "SSO state creation failed");
    }
  }

  private StatePayload readState(String state) {
    String raw = redisTemplate.opsForValue().get(STATE_PREFIX + state);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(raw, StatePayload.class);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }

  private void ensureEnabled() {
    if (!properties.enabled()) {
      throw new SsoException("SSO_DISABLED", HttpStatus.NOT_IMPLEMENTED, "SSO is disabled");
    }
  }

  private SsoProperties.Provider provider(String providerId) {
    return properties.enabledProviders().stream()
        .filter(item -> item.registrationId().equals(providerId))
        .findFirst()
        .orElseThrow(
            () ->
                new SsoException(
                    "SSO_PROVIDER_NOT_FOUND", HttpStatus.NOT_FOUND, "SSO provider not found"));
  }

  private record StatePayload(
      String provider, String nonce, String returnUrl, String issuer, String createdAt) {}
}
