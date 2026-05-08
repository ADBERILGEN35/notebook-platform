package com.notebook.lumen.identity.auth.application;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.auth.api.AuthMeResponse;
import com.notebook.lumen.identity.auth.api.LoginRequest;
import com.notebook.lumen.identity.auth.api.LogoutRequest;
import com.notebook.lumen.identity.auth.api.RefreshTokenRequest;
import com.notebook.lumen.identity.auth.api.RevokeAllRequest;
import com.notebook.lumen.identity.auth.api.RevokeAllResponse;
import com.notebook.lumen.identity.auth.api.SignupRequest;
import com.notebook.lumen.identity.mfa.application.MfaService;
import com.notebook.lumen.identity.notification.SecurityNotificationService;
import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.infrastructure.UserScimGroupMembershipRepository;
import com.notebook.lumen.identity.shared.exception.AccessTokenRequiredException;
import com.notebook.lumen.identity.shared.exception.EmailAlreadyExistsException;
import com.notebook.lumen.identity.shared.exception.InvalidCredentialsException;
import com.notebook.lumen.identity.shared.exception.InvalidRefreshTokenException;
import com.notebook.lumen.identity.shared.exception.InvalidTokenTypeException;
import com.notebook.lumen.identity.shared.exception.RefreshTokenUserMismatchException;
import com.notebook.lumen.identity.shared.exception.RefreshCookieRequiredException;
import com.notebook.lumen.identity.shared.exception.SessionNotFoundException;
import com.notebook.lumen.identity.shared.exception.UserDisabledException;
import com.notebook.lumen.identity.shared.exception.UserNotFoundException;
import com.notebook.lumen.identity.shared.exception.ValidationFailedException;
import com.notebook.lumen.identity.shared.security.EmailNormalizer;
import com.notebook.lumen.identity.shared.security.RefreshTokenHasher;
import com.notebook.lumen.identity.shared.security.jwt.JwtTokenService;
import com.notebook.lumen.identity.user.api.UserResponse;
import com.notebook.lumen.identity.user.domain.RefreshToken;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.RefreshTokenRepository;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import com.notebook.lumen.identity.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final String USER_LOGOUT_REASON = "USER_LOGOUT";
  private static final String USER_REVOKE_ALL_REASON = "USER_REVOKE_ALL";
  private static final String ACCOUNT_SECURITY_REASON = "ACCOUNT_SECURITY";

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final UserMapper userMapper;
  private final AuditService auditService;
  private final SecurityNotificationService securityNotificationService;
  private final MfaService mfaService;
  private final UserScimGroupMembershipRepository membershipRepository;
  private final ScimProperties scimProperties;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      UserMapper userMapper,
      AuditService auditService,
      SecurityNotificationService securityNotificationService,
      MfaService mfaService,
      UserScimGroupMembershipRepository membershipRepository,
      ScimProperties scimProperties) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
    this.userMapper = userMapper;
    this.auditService = auditService;
    this.securityNotificationService = securityNotificationService;
    this.mfaService = mfaService;
    this.membershipRepository = membershipRepository;
    this.scimProperties = scimProperties;
  }

  @Transactional
  public AuthResponse signup(SignupRequest request, HttpServletRequest httpRequest) {
    String email = normalizeEmailOrThrow(request.email());

    if (userRepository.findByEmail(email).isPresent()) {
      throw new EmailAlreadyExistsException(email);
    }

    validatePasswordPolicy(request.password());

    Instant now = Instant.now();
    UUID userId = UUID.randomUUID();
    String passwordHash = passwordEncoder.encode(request.password());

    User user =
        new User(
            userId,
            email,
            request.name(),
            request.avatarUrl(),
            passwordHash,
            UserStatus.ACTIVE,
            null,
            null,
            now,
            now,
            now,
            null);
    userRepository.save(user);
    auditService.record(
        "USER_SIGNED_UP",
        user.getId(),
        "USER",
        user.getId(),
        httpRequest,
        Map.of("status", "ACTIVE"));

    AuthResponse tokens = issueTokens(user, httpRequest, Map.of());
    return tokens;
  }

  @Transactional
  public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
    String email = normalizeEmailOrThrow(request.email());

    Optional<User> found = userRepository.findByEmail(email);
    if (found.isEmpty()) {
      auditService.record(
          "USER_LOGIN_FAILED",
          null,
          "USER",
          null,
          httpRequest,
          Map.of("reason", "INVALID_CREDENTIALS"));
      throw new InvalidCredentialsException();
    }
    User user = found.get();

    if (user.getDeletedAt() != null || user.getStatus() == UserStatus.DELETED) {
      throw new UserDisabledException();
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new UserDisabledException();
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      auditService.record(
          "USER_LOGIN_FAILED",
          user.getId(),
          "USER",
          user.getId(),
          httpRequest,
          Map.of("reason", "INVALID_CREDENTIALS"));
      throw new InvalidCredentialsException();
    }

    user.setLastLoginAt(Instant.now());
    userRepository.save(user);
    auditService.record(
        "USER_LOGIN_SUCCEEDED", user.getId(), "USER", user.getId(), httpRequest, Map.of());

    if (mfaService.isMfaRequired(user)) {
      return mfaService.startMfaChallenge(user);
    }
    return issueTokens(user, httpRequest, Map.of());
  }

  @Transactional
  public AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest, String refreshTokenFallback) {
    String refreshTokenPlaintext =
        request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
            ? request.refreshToken()
            : refreshTokenFallback;
    if (refreshTokenPlaintext == null || refreshTokenPlaintext.isBlank()) {
      throw new RefreshCookieRequiredException();
    }

    JwtTokenService.RefreshTokenJwtClaims decoded =
        jwtTokenService.decodeRefreshToken(refreshTokenPlaintext);

    User user =
        userRepository
            .findById(decoded.userId())
            .orElseThrow(() -> new UserNotFoundException(decoded.userId()));

    if (user.getDeletedAt() != null
        || user.getStatus() == UserStatus.DELETED
        || user.getStatus() != UserStatus.ACTIVE) {
      throw new UserDisabledException();
    }

    String tokenHash = RefreshTokenHasher.hash(refreshTokenPlaintext);
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(InvalidRefreshTokenException::new);

    if (!stored.getId().equals(decoded.refreshTokenId())) {
      throw new InvalidRefreshTokenException();
    }
    if (stored.getRevokedAt() != null) {
      // Reuse of revoked tokens is treated as invalid refresh.
      auditService.record(
          "REFRESH_TOKEN_REUSE_REJECTED",
          user.getId(),
          "REFRESH_TOKEN",
          stored.getId(),
          httpRequest,
          reuseMetadata(stored));
      throw new InvalidRefreshTokenException();
    }
    if (stored.getExpiresAt().isBefore(Instant.now())
        || stored.getExpiresAt().equals(Instant.now())) {
      throw new InvalidRefreshTokenException();
    }

    // Rotate refresh token.
    UUID newRefreshTokenId = UUID.randomUUID();
    Instant now = Instant.now();

    String newRefreshJwt = jwtTokenService.generateRefreshToken(user.getId(), newRefreshTokenId);
    String newRefreshHash = RefreshTokenHasher.hash(newRefreshJwt);
    Instant newExpiresAt = now.plusSeconds(jwtTokenService.refreshTokenTtlSeconds());

    RefreshToken newToken =
        new RefreshToken(
            newRefreshTokenId,
            user,
            newRefreshHash,
            newExpiresAt,
            null,
            null,
            now,
            extractClientIp(httpRequest),
            httpRequest.getHeader("User-Agent"));
    refreshTokenRepository.save(newToken);

    stored.markUsed(now);
    stored.revoke(now, newRefreshTokenId, "ROTATED", user.getId());
    refreshTokenRepository.save(stored);
    auditService.record(
        "REFRESH_TOKEN_ROTATED",
        user.getId(),
        "REFRESH_TOKEN",
        newRefreshTokenId,
        httpRequest,
        Map.of("replacedTokenId", stored.getId().toString()));

    String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail());
    long expiresIn = jwtTokenService.accessTokenTtlSeconds();

    UserResponse userResponse = userMapper.toResponse(user);
    return new AuthResponse(
        accessToken, newRefreshJwt, "Bearer", expiresIn, userResponse, false, null, java.util.List.of());
  }

  @Transactional
  public void logout(
      LogoutRequest request, Jwt accessToken, HttpServletRequest httpRequest, String refreshTokenFallback) {
    UUID authenticatedUserId = authenticatedAccessUserId(accessToken);
    String refreshTokenPlaintext =
        request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
            ? request.refreshToken()
            : refreshTokenFallback;
    if (refreshTokenPlaintext == null || refreshTokenPlaintext.isBlank()) {
      throw new RefreshCookieRequiredException();
    }

    JwtTokenService.RefreshTokenJwtClaims decoded =
        jwtTokenService.decodeRefreshToken(refreshTokenPlaintext);
    if (!authenticatedUserId.equals(decoded.userId())) {
      throw new RefreshTokenUserMismatchException();
    }

    String tokenHash = RefreshTokenHasher.hash(refreshTokenPlaintext);
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(InvalidRefreshTokenException::new);
    if (!stored.getId().equals(decoded.refreshTokenId())) {
      throw new InvalidRefreshTokenException();
    }

    if (stored.getRevokedAt() == null) {
      Instant now = Instant.now();
      stored.revoke(now, null, USER_LOGOUT_REASON, authenticatedUserId);
      refreshTokenRepository.save(stored);
      auditService.record(
          "REFRESH_TOKEN_REVOKED",
          authenticatedUserId,
          "REFRESH_TOKEN",
          stored.getId(),
          httpRequest,
          Map.of("reason", USER_LOGOUT_REASON, "tokenId", stored.getId().toString()));
    }
  }

  @Transactional
  public RevokeAllResponse revokeAll(
      RevokeAllRequest request, Jwt accessToken, HttpServletRequest httpRequest) {
    UUID authenticatedUserId = authenticatedAccessUserId(accessToken);
    String reason = revokeAllReason(request);
    Instant now = Instant.now();
    var activeTokens =
        refreshTokenRepository.findByUserIdAndRevokedAtIsNullAndExpiresAtAfter(
            authenticatedUserId, now);

    for (RefreshToken token : activeTokens) {
      token.revoke(now, null, reason, authenticatedUserId);
    }
    refreshTokenRepository.saveAll(activeTokens);
    User user =
        userRepository
            .findById(authenticatedUserId)
            .orElseThrow(() -> new UserNotFoundException(authenticatedUserId));

    auditService.record(
        "REFRESH_TOKENS_REVOKED_ALL",
        authenticatedUserId,
        "USER",
        authenticatedUserId,
        httpRequest,
        Map.of("revokedCount", activeTokens.size(), "reason", reason));
    securityNotificationService.refreshTokensRevoked(user, activeTokens.size(), httpRequest);
    return new RevokeAllResponse(activeTokens.size());
  }

  @Transactional(readOnly = true)
  public AuthMeResponse me(Jwt accessToken) {
    UUID authenticatedUserId = authenticatedAccessUserId(accessToken);
    User user =
        userRepository.findById(authenticatedUserId).orElseThrow(SessionNotFoundException::new);
    var roles = new ArrayList<String>();
    roles.add("ROLE_USER");
    roles.addAll(extractPlatformRoles(accessToken));
    return new AuthMeResponse(user.getId(), user.getEmail(), user.getName(), user.getAvatarUrl(), roles);
  }

  @Transactional
  public AuthResponse issueTokensForUser(
      User user, HttpServletRequest httpRequest, Map<String, Object> accessClaims) {
    user.setLastLoginAt(Instant.now());
    userRepository.save(user);
    return issueTokens(user, httpRequest, accessClaims);
  }

  private AuthResponse issueTokens(
      User user, HttpServletRequest httpRequest, Map<String, Object> accessClaims) {
    UUID refreshTokenId = UUID.randomUUID();
    Instant now = Instant.now();

    String refreshJwt = jwtTokenService.generateRefreshToken(user.getId(), refreshTokenId);
    String refreshHash = RefreshTokenHasher.hash(refreshJwt);

    Instant expiresAt = now.plusSeconds(jwtTokenService.refreshTokenTtlSeconds());

    RefreshToken refreshToken =
        new RefreshToken(
            refreshTokenId,
            user,
            refreshHash,
            expiresAt,
            null,
            null,
            now,
            extractClientIp(httpRequest),
            httpRequest.getHeader("User-Agent"));
    refreshTokenRepository.save(refreshToken);

    Map<String, Object> claims = new java.util.LinkedHashMap<>(accessClaims);
    var scimRoles = platformRolesFromScimGroups(user.getId());
    if (!scimRoles.isEmpty()) {
      var merged = new java.util.ArrayList<String>();
      Object existing = claims.get("platform_roles");
      if (existing instanceof Collection<?> c) {
        c.forEach(v -> merged.add(String.valueOf(v)));
      }
      merged.addAll(scimRoles);
      claims.put("platform_roles", merged.stream().map(r -> r.toUpperCase(Locale.ROOT)).distinct().toList());
    }

    String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail(), claims);
    long expiresIn = jwtTokenService.accessTokenTtlSeconds();

    UserResponse userResponse = userMapper.toResponse(user);
    return new AuthResponse(
        accessToken, refreshJwt, "Bearer", expiresIn, userResponse, false, null, java.util.List.of());
  }

  @Transactional
  public AuthResponse completeMfaLogin(String mfaSessionId, HttpServletRequest httpRequest) {
    UUID userId = mfaService.requireMfaSessionUser(mfaSessionId);
    User user =
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    mfaService.consumeMfaSession(mfaSessionId);
    return issueTokens(
        user,
        httpRequest,
        Map.of(
            "mfa_verified",
            true,
            "amr",
            java.util.List.of("pwd", "webauthn"),
            "mfa_verified_at",
            Instant.now().toString()));
  }

  private UUID authenticatedAccessUserId(Jwt accessToken) {
    if (accessToken == null) {
      throw new AccessTokenRequiredException();
    }
    Object tokenType = accessToken.getClaim("token_type");
    if (!(tokenType instanceof String value) || !ACCESS_TOKEN_TYPE.equals(value)) {
      throw new InvalidTokenTypeException();
    }
    try {
      return UUID.fromString(accessToken.getSubject());
    } catch (IllegalArgumentException e) {
      throw new InvalidTokenTypeException();
    }
  }

  private String revokeAllReason(RevokeAllRequest request) {
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      return USER_REVOKE_ALL_REASON;
    }
    String reason = request.reason().trim().toUpperCase();
    if (ACCOUNT_SECURITY_REASON.equals(reason) || USER_REVOKE_ALL_REASON.equals(reason)) {
      return reason;
    }
    throw new ValidationFailedException("reason must be USER_REVOKE_ALL or ACCOUNT_SECURITY");
  }

  private Map<String, Object> reuseMetadata(RefreshToken stored) {
    if (stored.getReplacedByTokenId() != null) {
      return Map.of(
          "reason",
          "REPLACED_TOKEN_REUSED",
          "tokenId",
          stored.getId().toString(),
          "replacedByTokenId",
          stored.getReplacedByTokenId().toString());
    }
    return Map.of("reason", "REVOKED_TOKEN_REUSED", "tokenId", stored.getId().toString());
  }

  private void validatePasswordPolicy(String password) {
    if (password == null) {
      throw new ValidationFailedException("Password is required");
    }
    boolean hasLetter = password.chars().anyMatch(Character::isLetter);
    boolean hasDigit = password.chars().anyMatch(Character::isDigit);
    if (password.length() < 10 || !hasLetter || !hasDigit) {
      throw new ValidationFailedException(
          "Password must be at least 10 chars and contain at least 1 letter and 1 digit");
    }
  }

  private String normalizeEmailOrThrow(String email) {
    String normalized = EmailNormalizer.normalize(email);
    if (normalized == null || normalized.isBlank()) {
      throw new ValidationFailedException("email is required");
    }
    return normalized;
  }

  private String extractClientIp(HttpServletRequest request) {
    String xf = request.getHeader("X-Forwarded-For");
    if (xf != null && !xf.isBlank()) {
      return xf.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private java.util.List<String> extractPlatformRoles(Jwt accessToken) {
    Object platformRoles = accessToken.getClaims().get("platform_roles");
    if (!(platformRoles instanceof Collection<?> roles)) {
      return java.util.List.of();
    }
    return roles.stream()
        .map(String::valueOf)
        .map(role -> role.trim().toUpperCase(Locale.ROOT))
        .filter(role -> role.equals("PLATFORM_ADMIN"))
        .distinct()
        .toList();
  }

  private java.util.List<String> platformRolesFromScimGroups(UUID userId) {
    if (!scimProperties.groupsEnabled()) {
      return java.util.List.of();
    }
    return membershipRepository.findByUserId(userId).stream()
        .filter(
            m -> {
              String display = m.getGroupDisplayName() == null ? "" : m.getGroupDisplayName().toLowerCase(Locale.ROOT);
              String external = m.getGroupExternalId() == null ? "" : m.getGroupExternalId().toLowerCase(Locale.ROOT);
              return scimProperties.adminGroupSet().stream()
                  .anyMatch(group -> group.equals(display) || group.equals(external));
            })
        .map(m -> "PLATFORM_ADMIN")
        .distinct()
        .toList();
  }
}
