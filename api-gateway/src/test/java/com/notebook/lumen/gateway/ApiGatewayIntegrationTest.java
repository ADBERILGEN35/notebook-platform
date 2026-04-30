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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "gateway.rate-limit.auth.replenish-rate=1",
      "gateway.rate-limit.auth.burst-capacity=1",
      "gateway.rate-limit.auth.requested-tokens=1",
      "gateway.rate-limit.protected-api.replenish-rate=100",
      "gateway.rate-limit.protected-api.burst-capacity=100",
      "gateway.rate-limit.protected-api.requested-tokens=1"
    })
class ApiGatewayIntegrationTest {

  private static final TestDownstream IDENTITY = TestDownstream.start("identity-service");
  private static final TestDownstream WORKSPACE = TestDownstream.start("workspace-service");
  private static final TestDownstream CONTENT = TestDownstream.start("content-service");
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
  }

  @BeforeEach
  void resetDownstream() {
    webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    IDENTITY.reset();
    WORKSPACE.reset();
    CONTENT.reset();
  }

  @AfterAll
  static void stopDownstreams() {
    IDENTITY.stop();
    WORKSPACE.stop();
    CONTENT.stop();
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

  private record TestRequest(String path, Map<String, List<String>> headers) {
    List<String> header(String name) {
      return headers.getOrDefault(name, List.of());
    }
  }

  private static final class TestDownstream {
    private final String serviceName;
    private final HttpServer server;
    private volatile TestRequest lastRequest;

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
    }

    void stop() {
      server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
      lastRequest =
          new TestRequest(exchange.getRequestURI().getPath(), exchange.getRequestHeaders());
      byte[] response =
          """
                {"status":"OK","service":"%s"}\
                """
              .formatted(serviceName)
              .getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }
  }
}
