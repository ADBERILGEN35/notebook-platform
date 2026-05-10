package com.notebook.lumen.identity.sso.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.identity.admin.AdminRbacProperties;
import com.notebook.lumen.identity.admin.AdminRbacService;
import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.auth.application.AuthService;
import com.notebook.lumen.identity.shared.exception.SsoException;
import com.notebook.lumen.identity.sso.SsoProperties;
import com.notebook.lumen.identity.sso.infrastructure.ExternalIdentityRepository;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.Mockito.mock;

class SsoServiceTest {

  @Test
  void providersRequireSsoEnabled() {
    SsoService service = service(properties(false));
    assertThatThrownBy(service::listProviders)
        .isInstanceOf(SsoException.class)
        .hasMessageContaining("disabled");
  }

  @Test
  void listProvidersReturnsConfiguredProviders() {
    SsoService service = service(properties(true));
    assertThat(service.listProviders()).hasSize(1);
    assertThat(service.listProviders().getFirst().registrationId()).isEqualTo("generic-oidc");
  }

  @Test
  void callbackRejectsStateMiss() {
    SsoService service = service(properties(true));
    assertThatThrownBy(
            () ->
                service.handleCallback(
                    "generic-oidc", "code", "missing-state", mock(HttpServletRequest.class)))
        .isInstanceOf(SsoException.class)
        .satisfies(
            ex ->
                assertThat(((SsoException) ex).getErrorCode())
                    .isIn("SSO_STATE_EXPIRED", "SSO_STATE_INVALID"));
  }

  @Test
  void callbackMapsAdminGroupIntoPlatformRoleClaim() throws Exception {
    SsoProperties props = properties(true);
    AuthService authService = mock(AuthService.class);
    UserRepository userRepository = mock(UserRepository.class);
    ExternalIdentityRepository identityRepository = mock(ExternalIdentityRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    OidcClient oidcClient = mock(OidcClient.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getRequestURL())
        .thenReturn(new StringBuffer("http://localhost:8081/auth/sso/generic-oidc/callback"));

    var payloadJson =
        mapper.writeValueAsString(
            new java.util.LinkedHashMap<String, String>() {
              {
                put("provider", "generic-oidc");
                put("nonce", "nonce-1");
                put("returnUrl", "/app");
                put("issuer", "https://issuer.example");
                put("createdAt", Instant.now().toString());
              }
            });
    when(valueOps.get("sso:state:state-1")).thenReturn(payloadJson);
    when(oidcClient.discover(any()))
        .thenReturn(
            new OidcProviderMetadata(
                "https://issuer.example/auth",
                "https://issuer.example/token",
                "https://issuer.example/jwks",
                "https://issuer.example"));
    when(oidcClient.exchangeAndValidate(any(), any(), any(), any()))
        .thenReturn(
            new OidcIdTokenProfile(
                "sub-1",
                "admin@example.com",
                true,
                List.of("notebook-admins"),
                List.of("pwd", "mfa"),
                "urn:mfa",
                "nonce-1",
                "{}"));
    User user =
        new User(
            UUID.randomUUID(),
            "admin@example.com",
            "Admin",
            null,
            "x",
            UserStatus.ACTIVE,
            Instant.now(),
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            null);
    when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(user));
    when(identityRepository.findByProviderAndSubject("generic-oidc", "sub-1")).thenReturn(Optional.empty());
    when(authService.issueTokensForUser(any(), any(), any()))
        .thenReturn(new AuthResponse("a", "r", "Bearer", 300, null, false, null, List.of()));

    SsoService service =
        new SsoService(
            props,
            rbacProps(false),
            mock(AdminRbacService.class),
            authService,
            userRepository,
            identityRepository,
            passwordEncoder,
            redis,
            mapper,
            oidcClient);

    SsoCallbackResult result = service.handleCallback("generic-oidc", "code", "state-1", request);
    assertThat(result.returnUrl()).isEqualTo("/app");
  }

  private SsoService service(SsoProperties props) {
    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(valueOps);
    return new SsoService(
        props,
        rbacProps(false),
        mock(AdminRbacService.class),
        mock(AuthService.class),
        mock(UserRepository.class),
        mock(ExternalIdentityRepository.class),
        mock(PasswordEncoder.class),
        redis,
        new ObjectMapper().findAndRegisterModules(),
        mock(OidcClient.class));
  }

  private static AdminRbacProperties rbacProps(boolean enabled) {
    return new AdminRbacProperties(
        enabled, true, "", "", "", "", "", "", "", "");
  }

  private SsoProperties properties(boolean enabled) {
    return new SsoProperties(
        enabled,
        300,
        false,
        "",
        "",
        List.of(
            new SsoProperties.Provider(
                "generic-oidc",
                "https://issuer.example",
                "client-id",
                "client-secret",
                "openid,email,profile",
                "email",
                "groups",
                "notebook-admins",
                "example.com")));
  }
}
