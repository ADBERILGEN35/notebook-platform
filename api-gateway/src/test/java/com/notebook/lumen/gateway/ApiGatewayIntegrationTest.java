package com.notebook.lumen.gateway;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.netty.http.client.HttpClient;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gateway.rate-limit.auth.replenish-rate=1",
      "gateway.rate-limit.auth.burst-capacity=1",
      "gateway.rate-limit.auth.requested-tokens=1",
      "gateway.rate-limit.protected-api.replenish-rate=100",
      "gateway.rate-limit.protected-api.burst-capacity=100",
      "gateway.rate-limit.protected-api.requested-tokens=1",
      "gateway.rate-limit.admin-audit.replenish-rate=100",
      "gateway.rate-limit.admin-audit.burst-capacity=100",
      "gateway.rate-limit.admin-audit.requested-tokens=1",
      "gateway.rate-limit.admin-write.replenish-rate=100",
      "gateway.rate-limit.admin-write.burst-capacity=100",
      "gateway.rate-limit.admin-write.requested-tokens=1",
      "gateway.rate-limit.admin-audit-export.replenish-rate=100",
      "gateway.rate-limit.admin-audit-export.burst-capacity=100",
      "gateway.rate-limit.admin-audit-export.requested-tokens=1",
      "gateway.admin.enabled=true",
      "gateway.admin.audit.enabled=true",
      "gateway.admin.audit-export.enabled=true",
      "gateway.admin.allowed-emails=ada@example.com",
      "gateway.admin.enterprise.enabled=false",
      "gateway.auth.token-transport=dual"
    })
class ApiGatewayIntegrationTest {

  private static final TestDownstream IDENTITY = TestDownstream.start("identity-service");
  private static final TestDownstream WORKSPACE = TestDownstream.start("workspace-service");
  private static final TestDownstream CONTENT = TestDownstream.start("content-service");
  private static final TestDownstream SEARCH = TestDownstream.start("search-service");
  private static final KeyPair KEYS = generateKeys();
  private static final String USER_ID = UUID.randomUUID().toString();
  private static final String USER_EMAIL = "ada@example.com";

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7")).withExposedPorts(6379);

  @LocalServerPort private int port;

  private WebTestClient webTestClient;

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("gateway.jwt.public-key", ApiGatewayIntegrationTest::publicKeyPem);
    registry.add("IDENTITY_SERVICE_URL", IDENTITY::baseUrl);
    registry.add("WORKSPACE_SERVICE_URL", WORKSPACE::baseUrl);
    registry.add("CONTENT_SERVICE_URL", CONTENT::baseUrl);
    registry.add("SEARCH_SERVICE_URL", SEARCH::baseUrl);
    registry.add(
        "gateway.admin.audit-proxy.service-jwt.active-kid", () -> "gateway-admin-audit-key-1");
    registry.add(
        "gateway.admin.audit-proxy.service-jwt.private-key",
        ApiGatewayIntegrationTest::privateKeyPem);
  }

  @BeforeEach
  void resetDownstream() {
    HttpClient reactorHttpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(60));
    webTestClient =
        WebTestClient.bindToServer(new ReactorClientHttpConnector(reactorHttpClient))
            .baseUrl("http://localhost:" + port)
            .build();
    IDENTITY.reset();
    WORKSPACE.reset();
    CONTENT.reset();
    SEARCH.reset();
  }

  @AfterAll
  static void stopDownstreams() {
    IDENTITY.stop();
    WORKSPACE.stop();
    CONTENT.stop();
    SEARCH.stop();
  }

  @Test
  void authLogin_isPublic_andRoutesToIdentityService() {
    webTestClient
        .post()
        .uri("/auth/login")
        .bodyValue(Map.of("email", "ada@example.com", "password", "Password1234"))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .exists("X-Request-Id")
        .expectBody()
        .jsonPath("$.service")
        .isEqualTo("identity-service");
  }

  @Test
  void authSignup_corsPreflight_returnsOkWithoutJwt() {
    webTestClient
        .options()
        .uri("/auth/signup")
        .header("Origin", "http://localhost:5173")
        .header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "content-type")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
  }

  @Test
  void protectedRoute_withoutToken_returns401() {
    webTestClient
        .get()
        .uri("/workspaces/test")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .exists("X-Request-Id")
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("MISSING_ACCESS_TOKEN")
        .jsonPath("$.requestId")
        .exists();
  }

  @Test
  void adminEnterpriseStatus_whenDisabled_returns404() {
    webTestClient
        .get()
        .uri("/admin/enterprise/status")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("ADMIN_ENTERPRISE_DISABLED");
  }

  @Test
  void adminAudit_withoutToken_returns401() {
    webTestClient
        .get()
        .uri("/admin/audit-events?source=identity")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("MISSING_ACCESS_TOKEN");
  }

  @Test
  void adminAudit_nonAdmin_returns403() {
    webTestClient
        .get()
        .uri("/admin/audit-events?source=identity")
        .headers(
            headers -> headers.setBearerAuth(jwt(USER_ID, "member@example.com", "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("ADMIN_ACCESS_DENIED");
  }

  @Test
  void adminAudit_invalidSource_returns400() {
    webTestClient
        .get()
        .uri("/admin/audit-events?source=unknown")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("INVALID_AUDIT_SOURCE");
  }

  @Test
  void adminAudit_invalidFilter_returns400() {
    webTestClient
        .get()
        .uri("/admin/audit-events?source=workspace&actorUserId=not-uuid")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("INVALID_AUDIT_FILTER");
  }

  @Test
  void adminAuditExport_requiresRange() {
    webTestClient
        .get()
        .uri("/admin/audit-events/export?source=identity&format=csv")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("AUDIT_EXPORT_RANGE_REQUIRED");
  }

  @Test
  void adminAuditExport_csv_success() {
    webTestClient
        .get()
        .uri(
            "/admin/audit-events/export?source=identity&format=csv&createdFrom=2026-01-01T00:00:00Z&createdTo=2026-01-02T00:00:00Z")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith("text/csv")
        .expectHeader()
        .valueMatches("Content-Disposition", ".*attachment.*audit-identity-.*\\.csv.*");
  }

  @Test
  void adminAudit_allowlistAdmin_proxiesWithServiceJwt() {
    webTestClient
        .get()
        .uri(
            "/admin/audit-events?source=identity&page=1&size=25&sort=createdAt,desc&eventType=LOGIN_SUCCESS")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.service")
        .isEqualTo("identity-service");

    TestRequest request = IDENTITY.lastRequest();
    org.assertj.core.api.Assertions.assertThat(request.path()).isEqualTo("/internal/audit-events");
    org.assertj.core.api.Assertions.assertThat(request.query())
        .contains("page=1")
        .contains("size=25")
        .contains("sort=createdAt,desc")
        .contains("eventType=LOGIN_SUCCESS");
    org.assertj.core.api.Assertions.assertThat(request.header("X-Service-Authorization"))
        .singleElement()
        .asString()
        .startsWith("Bearer ");
  }

  @Test
  void adminAudit_targetUnavailable_returns503() {
    CONTENT.respondWithStatus("/internal/audit-events", 503, "{\"error\":\"down\"}");
    webTestClient
        .get()
        .uri("/admin/audit-events?source=content")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isEqualTo(503)
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("AUDIT_SOURCE_UNAVAILABLE");
  }

  @Test
  void adminAudit_internalAuthFailure_isMappedSafely() {
    WORKSPACE.respondWithStatus(
        "/internal/audit-events", 403, "{\"errorCode\":\"AUDIT_ACCESS_DENIED\"}");
    webTestClient
        .get()
        .uri("/admin/audit-events?source=workspace")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isEqualTo(502)
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("AUDIT_PROXY_INTERNAL_AUTH_FAILED")
        .jsonPath("$.message")
        .isEqualTo("Internal audit authorization failed");
  }

  @Test
  void protectedRoute_withInvalidToken_returns401() {
    webTestClient
        .get()
        .uri("/workspaces/test")
        .headers(headers -> headers.setBearerAuth("not-a-jwt"))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("INVALID_ACCESS_TOKEN");
  }

  @Test
  void protectedRoute_withExpiredToken_returns401() {
    webTestClient
        .get()
        .uri("/workspaces/test")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", -120, -60)))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("EXPIRED_ACCESS_TOKEN");
  }

  @Test
  void protectedRoute_withRefreshTokenType_returns401() {
    webTestClient
        .get()
        .uri("/workspaces/test")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "refresh", 0, 300)))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("INVALID_TOKEN_TYPE");
  }

  @Test
  void protectedRoute_withAccessToken_routesAndPropagatesHeaders() {
    String workspaceId = UUID.randomUUID().toString();

    webTestClient
        .get()
        .uri("/workspaces/test")
        .headers(
            headers -> {
              headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300));
              headers.set("X-Workspace-Id", workspaceId);
              headers.set("X-User-Id", "spoofed-user");
              headers.set("X-User-Email", "spoofed@example.com");
              headers.set("X-User-Roles", "admin");
              headers.set("X-Workspace-Role", "owner");
            })
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.service")
        .isEqualTo("workspace-service");

    TestRequest request = WORKSPACE.lastRequest();
    org.assertj.core.api.Assertions.assertThat(request.header("X-User-Id"))
        .containsExactly(USER_ID);
    org.assertj.core.api.Assertions.assertThat(request.header("X-User-Email"))
        .containsExactly(USER_EMAIL);
    org.assertj.core.api.Assertions.assertThat(request.header("X-Workspace-Id"))
        .containsExactly(workspaceId);
    org.assertj.core.api.Assertions.assertThat(request.header("X-User-Roles")).isEmpty();
    org.assertj.core.api.Assertions.assertThat(request.header("X-Workspace-Role")).isEmpty();
    org.assertj.core.api.Assertions.assertThat(request.header("X-Request-Id")).hasSize(1);
  }

  @Test
  void invalidWorkspaceId_returns400() {
    webTestClient
        .get()
        .uri("/workspaces/test")
        .headers(
            headers -> {
              headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300));
              headers.set("X-Workspace-Id", "not-a-uuid");
            })
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("INVALID_WORKSPACE_ID");
  }

  @Test
  void searchRoute_isProtectedAndRoutesToSearchService() {
    webTestClient
        .get()
        .uri("/search/notes?workspaceId=" + UUID.randomUUID() + "&q=roadmap")
        .headers(headers -> headers.setBearerAuth(jwt(USER_ID, USER_EMAIL, "access", 0, 300)))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.service")
        .isEqualTo("search-service");

    TestRequest request = SEARCH.lastRequest();
    org.assertj.core.api.Assertions.assertThat(request.path()).isEqualTo("/search/notes");
    org.assertj.core.api.Assertions.assertThat(request.header("X-User-Id"))
        .containsExactly(USER_ID);
  }

  @Test
  void authRateLimitExceeded_returns429() {
    webTestClient
        .post()
        .uri("/auth/refresh")
        .bodyValue(Map.of("refreshToken", "placeholder"))
        .exchange()
        .expectStatus()
        .isOk();

    webTestClient
        .post()
        .uri("/auth/refresh")
        .bodyValue(Map.of("refreshToken", "placeholder"))
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("RATE_LIMIT_EXCEEDED")
        .jsonPath("$.requestId")
        .exists();
  }

  @Test
  void cookieAccessToken_routesWithoutAuthorizationHeader() {
    webTestClient
        .get()
        .uri("/workspaces/test")
        .cookie("__Host-np_access", jwt(USER_ID, USER_EMAIL, "access", 0, 300))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.service")
        .isEqualTo("workspace-service");
  }

  @Test
  void cookieModeUnsafeRequest_requiresCsrfHeader() {
    webTestClient
        .post()
        .uri("/workspaces")
        .cookie("__Host-np_access", jwt(USER_ID, USER_EMAIL, "access", 0, 300))
        .bodyValue(Map.of("name", "ws"))
        .exchange()
        .expectStatus()
        .isEqualTo(403)
        .expectBody()
        .jsonPath("$.errorCode")
        .isEqualTo("CSRF_TOKEN_REQUIRED");
  }

  @Test
  void cookieModeUnsafeRequest_withMatchingCsrf_succeeds() {
    webTestClient
        .post()
        .uri("/workspaces")
        .cookie("__Host-np_access", jwt(USER_ID, USER_EMAIL, "access", 0, 300))
        .cookie("NP-XSRF-TOKEN", "csrf-value")
        .header("X-CSRF-Token", "csrf-value")
        .bodyValue(Map.of("name", "ws"))
        .exchange()
        .expectStatus()
        .isOk();
  }

  private static KeyPair generateKeys() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String publicKeyPem() {
    RSAPublicKey publicKey = (RSAPublicKey) KEYS.getPublic();
    String encoded =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(publicKey.getEncoded());
    return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
  }

  private static String privateKeyPem() {
    RSAPrivateKey privateKey = (RSAPrivateKey) KEYS.getPrivate();
    String encoded =
        Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(privateKey.getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----";
  }

  private static String jwt(
      String subject,
      String email,
      String tokenType,
      long issuedOffsetSeconds,
      long expiresOffsetSeconds) {
    try {
      Instant now = Instant.now();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .subject(subject)
              .claim("email", email)
              .claim("token_type", tokenType)
              .issueTime(Date.from(now.plusSeconds(issuedOffsetSeconds)))
              .expirationTime(Date.from(now.plusSeconds(expiresOffsetSeconds)))
              .build();
      SignedJWT signedJwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
      signedJwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate()));
      return signedJwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private record TestRequest(String path, String query, Map<String, List<String>> headers) {
    List<String> header(String name) {
      return headers.getOrDefault(name, List.of());
    }
  }

  private static final class TestDownstream {
    private final String serviceName;
    private final HttpServer server;
    private volatile TestRequest lastRequest;
    private volatile String responsePathPrefix;
    private volatile int responseStatus = 200;
    private volatile String responseBody;

    private TestDownstream(String serviceName, HttpServer server) {
      this.serviceName = serviceName;
      this.server = server;
    }

    static TestDownstream start(String serviceName) {
      try {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        TestDownstream downstream = new TestDownstream(serviceName, server);
        server.createContext("/", downstream::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return downstream;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    String baseUrl() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    TestRequest lastRequest() {
      return lastRequest;
    }

    void reset() {
      lastRequest = null;
      responsePathPrefix = null;
      responseStatus = 200;
      responseBody = null;
    }

    void respondWithStatus(String pathPrefix, int status, String body) {
      this.responsePathPrefix = pathPrefix;
      this.responseStatus = status;
      this.responseBody = body;
    }

    void stop() {
      server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
      lastRequest =
          new TestRequest(
              exchange.getRequestURI().getPath(),
              exchange.getRequestURI().getRawQuery(),
              exchange.getRequestHeaders());
      int status = 200;
      String body =
          """
                {"status":"OK","service":"%s"}\
                """
              .formatted(serviceName);
      if (responsePathPrefix != null
          && exchange.getRequestURI().getPath().startsWith(responsePathPrefix)) {
        status = responseStatus;
        body = responseBody == null ? "{}" : responseBody;
      }
      byte[] response = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }
  }
}
