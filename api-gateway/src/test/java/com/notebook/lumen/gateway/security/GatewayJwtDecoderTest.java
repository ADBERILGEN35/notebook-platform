package com.notebook.lumen.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.notebook.lumen.gateway.config.GatewayJwtProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.test.StepVerifier;

class GatewayJwtDecoderTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @TempDir Path tempDir;

  @Test
  void jwtDecoder_acceptsValidTokenFromJwksUri() throws Exception {
    KeyPair keys = keyPair();
    String token = token(keys, "key-1", "access", Instant.now().plusSeconds(60));

    withJwksServer(
        keys,
        "key-1",
        jwksUri -> {
          ReactiveJwtDecoder decoder = decoder(new GatewayJwtProperties(jwksUri, "", ""));

          StepVerifier.create(decoder.decode(token))
              .assertNext(jwt -> assertThat(jwt.getHeaders()).containsEntry("kid", "key-1"))
              .verifyComplete();
        });
  }

  @Test
  void jwtDecoder_rejectsUnknownKid() throws Exception {
    KeyPair keys = keyPair();
    String token = token(keys, "unknown", "access", Instant.now().plusSeconds(60));

    withJwksServer(
        keys,
        "key-1",
        jwksUri ->
            StepVerifier.create(decoder(new GatewayJwtProperties(jwksUri, "", "")).decode(token))
                .expectError()
                .verify());
  }

  @Test
  void jwtDecoder_rejectsExpiredToken() throws Exception {
    KeyPair keys = keyPair();
    String token = token(keys, "key-1", "access", Instant.now().minusSeconds(60));

    withJwksServer(
        keys,
        "key-1",
        jwksUri ->
            StepVerifier.create(decoder(new GatewayJwtProperties(jwksUri, "", "")).decode(token))
                .expectError()
                .verify());
  }

  @Test
  void jwtDecoder_rejectsRefreshTokenType() throws Exception {
    KeyPair keys = keyPair();
    String token = token(keys, "key-1", "refresh", Instant.now().plusSeconds(60));

    withJwksServer(
        keys,
        "key-1",
        jwksUri ->
            StepVerifier.create(decoder(new GatewayJwtProperties(jwksUri, "", "")).decode(token))
                .expectError()
                .verify());
  }

  @Test
  void jwtDecoder_supportsStaticPublicKeyFallback() throws Exception {
    KeyPair keys = keyPair();
    Path publicKeyPath = tempDir.resolve("public.pem");
    Files.writeString(publicKeyPath, pem("PUBLIC KEY", keys.getPublic().getEncoded()));
    String token = token(keys, "key-1", "access", Instant.now().plusSeconds(60));

    ReactiveJwtDecoder decoder =
        decoder(new GatewayJwtProperties("", publicKeyPath.toString(), ""));

    StepVerifier.create(decoder.decode(token)).expectNextCount(1).verifyComplete();
  }

  private ReactiveJwtDecoder decoder(GatewayJwtProperties properties) {
    return new GatewaySecurityConfig().jwtDecoder(properties, new JwtPublicKeyLoader(properties));
  }

  private void withJwksServer(KeyPair keys, String kid, ThrowingConsumer<String> test)
      throws Exception {
    RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).keyID(kid).build();
    String jwks = objectMapper.writeValueAsString(new JWKSet(rsaKey).toJSONObject(false));
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/.well-known/jwks.json",
        exchange -> {
          byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      test.accept("http://localhost:" + server.getAddress().getPort() + "/.well-known/jwks.json");
    } finally {
      server.stop(0);
    }
  }

  private String token(KeyPair keys, String kid, String tokenType, Instant expiresAt) {
    RSAKey rsaKey =
        new RSAKey.Builder((RSAPublicKey) keys.getPublic())
            .privateKey((RSAPrivateKey) keys.getPrivate())
            .keyID(kid)
            .build();
    JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    return encoder
        .encode(
            JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build(),
                JwtClaimsSet.builder()
                    .subject(UUID.randomUUID().toString())
                    .claim("email", "test@example.com")
                    .claim("token_type", tokenType)
                    .issuedAt(expiresAt.minusSeconds(60))
                    .expiresAt(expiresAt)
                    .build()))
        .getTokenValue();
  }

  private KeyPair keyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private String pem(String marker, byte[] der) {
    return "-----BEGIN "
        + marker
        + "-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der)
        + "\n-----END "
        + marker
        + "-----\n";
  }

  @FunctionalInterface
  private interface ThrowingConsumer<T> {
    void accept(T value) throws Exception;
  }
}
