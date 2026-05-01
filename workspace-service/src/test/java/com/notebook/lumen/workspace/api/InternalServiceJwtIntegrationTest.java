package com.notebook.lumen.workspace.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "workspace.identity.service-url=http://127.0.0.1:1",
      "workspace.invitations.expose-token-in-response=true",
      "workspace.internal.auth-mode=service-jwt",
      "workspace.internal.primary-token=test-static-token",
      "workspace.internal.trusted-content-service.kid=content-key-1",
      "workspace.internal.trusted-content-service.issuer=content-service",
      "workspace.internal.trusted-content-service.audience=workspace-service",
      "workspace.internal.trusted-content-service.clock-skew-seconds=0",
      "workspace.internal.trusted-content-service.allowed-scopes=internal:workspace:permission:read,internal:workspace:tag:read"
    })
class InternalServiceJwtIntegrationTest {
  private static final KeyPair KEY_PAIR = keyPair();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("notebook_platform")
          .withUsername("notebook")
          .withPassword("notebook");

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add(
        "workspace.internal.trusted-content-service.public-key", () -> publicPem(KEY_PAIR));
  }

  @LocalServerPort private int port;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void serviceJwtMode_requiresValidServiceJwt() throws Exception {
    UUID ownerId = UUID.randomUUID();
    JsonNode notebook = createNotebook(ownerId);
    String path =
        "/internal/notebooks/" + notebook.get("id").asText() + "/permissions?userId=" + ownerId;

    assertThat(sendInternal(path, null, null, 401).get("errorCode").asText())
        .isEqualTo("INTERNAL_AUTH_REQUIRED");
    assertThat(sendInternal(path, null, "test-static-token", 401).get("errorCode").asText())
        .isEqualTo("INTERNAL_AUTH_REQUIRED");

    JsonNode valid =
        sendInternal(
            path,
            serviceToken(
                "workspace-service",
                "content-service",
                "service",
                "content-key-1",
                60,
                "internal:workspace:permission:read"),
            null,
            200);
    assertThat(valid.get("canManage").asBoolean()).isTrue();

    assertThat(
            sendInternal(
                    path,
                    serviceToken(
                        "other",
                        "content-service",
                        "service",
                        "content-key-1",
                        60,
                        "internal:workspace:permission:read"),
                    null,
                    401)
                .get("errorCode")
                .asText())
        .isEqualTo("INVALID_SERVICE_AUDIENCE");
    assertThat(
            sendInternal(
                    path,
                    serviceToken(
                        "workspace-service",
                        "other",
                        "service",
                        "content-key-1",
                        60,
                        "internal:workspace:permission:read"),
                    null,
                    401)
                .get("errorCode")
                .asText())
        .isEqualTo("INVALID_SERVICE_ISSUER");
    assertThat(
            sendInternal(
                    path,
                    serviceToken(
                        "workspace-service",
                        "content-service",
                        "service",
                        "content-key-1",
                        -60,
                        "internal:workspace:permission:read"),
                    null,
                    401)
                .get("errorCode")
                .asText())
        .isEqualTo("EXPIRED_SERVICE_JWT");
    assertThat(
            sendInternal(
                    path,
                    serviceToken(
                        "workspace-service",
                        "content-service",
                        "access",
                        "content-key-1",
                        60,
                        "internal:workspace:permission:read"),
                    null,
                    401)
                .get("errorCode")
                .asText())
        .isEqualTo("INVALID_SERVICE_JWT");
    assertThat(
            sendInternal(
                    path,
                    serviceToken(
                        "workspace-service",
                        "content-service",
                        "service",
                        "unknown",
                        60,
                        "internal:workspace:permission:read"),
                    null,
                    401)
                .get("errorCode")
                .asText())
        .isEqualTo("INVALID_SERVICE_JWT");
    assertThat(
            sendInternal(
                    path,
                    serviceToken(
                        "workspace-service",
                        "content-service",
                        "service",
                        "content-key-1",
                        60,
                        "internal:workspace:tag:read"),
                    null,
                    403)
                .get("errorCode")
                .asText())
        .isEqualTo("INSUFFICIENT_SERVICE_SCOPE");
  }

  private JsonNode createNotebook(UUID ownerId) throws Exception {
    JsonNode workspace =
        send(
            "POST",
            "/workspaces",
            ownerId,
            """
            {"name":"Service Jwt Team","type":"TEAM"}\
            """,
            null,
            null,
            201);
    return send(
        "POST",
        "/workspaces/" + workspace.get("id").asText() + "/notebooks",
        ownerId,
        """
            {"name":"Internal","icon":"lock"}\
            """,
        null,
        null,
        201);
  }

  private JsonNode sendInternal(String path, String serviceJwt, String internalToken, int status)
      throws Exception {
    return send("GET", path, UUID.randomUUID(), null, serviceJwt, internalToken, status);
  }

  private JsonNode send(
      String method,
      String path,
      UUID userId,
      String body,
      String serviceJwt,
      String internalToken,
      int expectedStatus)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-User-Id", userId.toString())
            .header("X-User-Email", "user-" + userId + "@example.com");
    if (serviceJwt != null) {
      builder.header("X-Service-Authorization", "Bearer " + serviceJwt);
    }
    if (internalToken != null) {
      builder.header("X-Internal-Token", internalToken);
    }
    HttpRequest.BodyPublisher publisher =
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body);
    HttpResponse<String> response =
        httpClient.send(
            builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expectedStatus);
    if (serviceJwt != null) {
      assertThat(response.body()).doesNotContain(serviceJwt);
    }
    return response.body() == null || response.body().isBlank()
        ? objectMapper.createObjectNode()
        : objectMapper.readTree(response.body());
  }

  private String serviceToken(
      String audience, String issuer, String tokenType, String kid, long ttlSeconds, String scope) {
    if ("service".equals(tokenType) && "content-key-1".equals(kid) && ttlSeconds > 0) {
      return new ServiceJwtSigner(
              new ServiceJwtProperties(
                  kid,
                  privatePem(KEY_PAIR),
                  "",
                  issuer,
                  "service:" + issuer,
                  issuer,
                  Duration.ofSeconds(ttlSeconds)))
          .sign(audience, scope);
    }
    try {
      Instant now = Instant.now();
      JWTClaimsSet claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer)
              .subject("service:" + issuer)
              .audience(audience)
              .claim("scope", scope)
              .claim("service_name", issuer)
              .claim("token_type", tokenType)
              .jwtID(UUID.randomUUID().toString())
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plusSeconds(ttlSeconds)))
              .build();
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).type(JOSEObjectType.JWT).build(),
              claims);
      jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
      return jwt.serialize();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static KeyPair keyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static String privatePem(KeyPair keyPair) {
    return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
  }

  private static String publicPem(KeyPair keyPair) {
    return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
  }

  private static String pem(String type, byte[] der) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
        + "\n-----END "
        + type
        + "-----";
  }
}
