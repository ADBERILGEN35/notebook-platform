package com.notebook.lumen.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.identity.audit.AuditEventRepository;
import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.auth.api.LoginRequest;
import com.notebook.lumen.identity.auth.api.RefreshTokenRequest;
import com.notebook.lumen.identity.auth.api.SignupRequest;
import com.notebook.lumen.identity.shared.exception.ErrorResponse;
import com.notebook.lumen.identity.shared.security.RefreshTokenHasher;
import com.notebook.lumen.identity.user.domain.RefreshToken;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.RefreshTokenRepository;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("notebook_platform")
          .withUsername("notebook")
          .withPassword("notebook");

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort private int port;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Autowired private UserRepository userRepository;

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private JwtDecoder jwtDecoder;

  @Autowired private AuditEventRepository auditEventRepository;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void signup_thenLogin_thenRefresh_rotation_andReuseDetection() throws Exception {
    String email = "user-" + UUID.randomUUID() + "@example.com";
    String password = "Password1234";
    String name = "Ada";

    SignupRequest signup = new SignupRequest(email, password, name, null);
    AuthResponse signupResp = postJson("/auth/signup", signup, AuthResponse.class);

    // Signup sets ACTIVE, but lastLoginAt should remain null until login.
    User userAfterSignup = userRepository.findById(signupResp.user().id()).orElseThrow();
    assertThat(userAfterSignup.getLastLoginAt()).isNull();

    LoginRequest login = new LoginRequest(email, password);
    AuthResponse loginResp = postJson("/auth/login", login, AuthResponse.class);

    User userAfterLogin = userRepository.findById(loginResp.user().id()).orElseThrow();
    assertThat(userAfterLogin.getLastLoginAt()).isNotNull();

    String loginRefreshHash = RefreshTokenHasher.hash(loginResp.refreshToken());
    RefreshToken storedBeforeRefresh =
        refreshTokenRepository.findByTokenHash(loginRefreshHash).orElseThrow();
    assertThat(storedBeforeRefresh.getRevokedAt()).isNull();
    assertThat(storedBeforeRefresh.getExpiresAt()).isAfter(Instant.now());

    Jwt decoded = jwtDecoder.decode(loginResp.refreshToken());
    assertThat(decoded.getSubject()).isEqualTo(loginResp.user().id().toString());
    assertThat(decoded.getHeaders()).containsKey("kid");
    assertThat((String) decoded.getClaim("token_type")).isEqualTo("refresh");
    assertThat((String) decoded.getClaim("jti")).isEqualTo(storedBeforeRefresh.getId().toString());

    RefreshTokenRequest refreshRequest1 = new RefreshTokenRequest(loginResp.refreshToken());
    AuthResponse refreshResp1 = postJson("/auth/refresh", refreshRequest1, AuthResponse.class);

    assertThat(refreshResp1.refreshToken()).isNotEqualTo(loginResp.refreshToken());
    assertThat(jwtDecoder.decode(refreshResp1.accessToken()).getHeaders()).containsKey("kid");

    // Reusing revoked refresh token must fail.
    HttpResponse<String> reuseResponse = postJsonRaw("/auth/refresh", refreshRequest1);
    assertThat(reuseResponse.statusCode()).isEqualTo(401);

    ErrorResponse error = objectMapper.readValue(reuseResponse.body(), ErrorResponse.class);
    assertThat(error.errorCode()).isEqualTo("INVALID_REFRESH_TOKEN");

    String oldRefreshHash = RefreshTokenHasher.hash(loginResp.refreshToken());
    RefreshToken oldToken = refreshTokenRepository.findByTokenHash(oldRefreshHash).orElseThrow();
    assertThat(oldToken.getRevokedAt()).isNotNull();
    assertThat(oldToken.getReplacedByTokenId()).isNotNull();

    String newRefreshHash = RefreshTokenHasher.hash(refreshResp1.refreshToken());
    RefreshToken newToken = refreshTokenRepository.findByTokenHash(newRefreshHash).orElseThrow();
    assertThat(newToken.getId()).isEqualTo(oldToken.getReplacedByTokenId());
    assertThat(newToken.getRevokedAt()).isNull();
    assertThat(auditEventRepository.count()).isGreaterThanOrEqualTo(4);
  }

  @Test
  void duplicateSignup_returns409Conflict() throws Exception {
    String email = "dup-" + UUID.randomUUID() + "@example.com";
    String password = "Password1234";
    String name = "Ada";

    SignupRequest signup = new SignupRequest(email, password, name, null);
    postJson("/auth/signup", signup, AuthResponse.class);

    HttpResponse<String> second = postJsonRaw("/auth/signup", signup);
    assertThat(second.statusCode()).isEqualTo(409);

    ErrorResponse error = objectMapper.readValue(second.body(), ErrorResponse.class);
    assertThat(error.errorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
  }

  @Test
  void validationError_includesRequestIdAndFieldErrors() throws Exception {
    HttpResponse<String> response =
        postJsonRaw("/auth/signup", new SignupRequest("", "short", "", null));
    assertThat(response.statusCode()).isEqualTo(400);

    ErrorResponse error = objectMapper.readValue(response.body(), ErrorResponse.class);
    assertThat(error.errorCode()).isEqualTo("VALIDATION_ERROR");
    assertThat(error.requestId()).isNotBlank();
    assertThat(error.fieldErrors()).isNotEmpty();
  }

  @Test
  void jwksEndpoint_returnsPublicKeySet() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/.well-known/jwks.json"))
            .GET()
            .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"keys\"");
    assertThat(response.body()).contains("\"kid\"");
    assertThat(response.body()).doesNotContain("\"d\"");
    assertThat(response.body()).doesNotContain("PRIVATE KEY");
  }

  @Test
  void wrongPassword_login_returns401() throws Exception {
    String email = "wp-" + UUID.randomUUID() + "@example.com";
    String password = "Password1234";
    String name = "Ada";

    SignupRequest signup = new SignupRequest(email, password, name, null);
    postJson("/auth/signup", signup, AuthResponse.class);

    LoginRequest wrongLogin = new LoginRequest(email, "WrongPassword123");
    HttpResponse<String> resp = postJsonRaw("/auth/login", wrongLogin);
    assertThat(resp.statusCode()).isEqualTo(401);

    ErrorResponse error = objectMapper.readValue(resp.body(), ErrorResponse.class);
    assertThat(error.errorCode()).isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void disabledAndDeletedUsers_cannotLogin() throws Exception {
    String disabledEmail = "disabled-" + UUID.randomUUID() + "@example.com";
    String deletedEmail = "deleted-" + UUID.randomUUID() + "@example.com";
    String password = "Password1234";

    Instant now = Instant.now();

    User disabledUser =
        new User(
            UUID.randomUUID(),
            disabledEmail,
            "Disabled",
            null,
            passwordEncoder.encode(password),
            UserStatus.DISABLED,
            null,
            null,
            now,
            now,
            now,
            null);
    userRepository.save(disabledUser);

    User deletedUser =
        new User(
            UUID.randomUUID(),
            deletedEmail,
            "Deleted",
            null,
            passwordEncoder.encode(password),
            UserStatus.DELETED,
            null,
            null,
            now,
            now,
            now,
            now);
    userRepository.save(deletedUser);

    HttpResponse<String> disabledResp =
        postJsonRaw("/auth/login", new LoginRequest(disabledEmail, password));
    assertThat(disabledResp.statusCode()).isEqualTo(403);
    ErrorResponse disabledError = objectMapper.readValue(disabledResp.body(), ErrorResponse.class);
    assertThat(disabledError.errorCode()).isEqualTo("USER_DISABLED");

    HttpResponse<String> deletedResp =
        postJsonRaw("/auth/login", new LoginRequest(deletedEmail, password));
    assertThat(deletedResp.statusCode()).isEqualTo(403);
    ErrorResponse deletedError = objectMapper.readValue(deletedResp.body(), ErrorResponse.class);
    assertThat(deletedError.errorCode()).isEqualTo("USER_DISABLED");
  }

  private <T> T postJson(String path, Object body, Class<T> responseType) throws Exception {
    HttpResponse<String> response = postJsonRaw(path, body);
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new AssertionError(
          "Request failed: " + response.statusCode() + ", body=" + response.body());
    }
    return objectMapper.readValue(response.body(), responseType);
  }

  private HttpResponse<String> postJsonRaw(String path, Object body) throws Exception {
    String url = "http://localhost:" + port + path;
    String json = objectMapper.writeValueAsString(body);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
