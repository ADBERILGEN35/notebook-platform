package com.notebook.lumen.identity.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.notebook.lumen.identity.shared.config.JwtProperties;
import com.notebook.lumen.identity.shared.exception.InvalidRefreshTokenException;
import com.notebook.lumen.identity.shared.security.jwt.JwtTokenService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenServiceTest {

  @Test
  void accessToken_containsExpectedClaims_andRefreshTokenValidates() {
    JwtProperties props = new JwtProperties();
    props.setAccessTokenTtlSeconds(60);
    props.setRefreshTokenTtlSeconds(3600);
    props.setPrivateKeyPath("");
    props.setPublicKeyPath("");

    JwtKeyProvider keyProvider = new JwtKeyProvider(props);
    JwtKeyProvider.JwtRsaKeys keys = keyProvider.loadOrGenerateKeys();

    RSAKey rsaKey =
        new RSAKey.Builder(keys.publicKey())
            .privateKey(keys.privateKey())
            .keyID(keys.kid())
            .build();

    JWKSet jwkSet = new JWKSet(rsaKey);
    JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);

    JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
    JwtDecoder decoder = new JwtKeySetDecoder(keyProvider);

    JwtTokenService service = new JwtTokenService(encoder, decoder, props, keyProvider);

    UUID userId = UUID.randomUUID();
    String email = "test@example.com";
    String accessToken = service.generateAccessToken(userId, email);

    Jwt jwt = decoder.decode(accessToken);
    assertThat(jwt.getHeaders()).containsEntry("kid", keys.kid());
    assertThat(jwt.getSubject()).isEqualTo(userId.toString());
    assertThat((String) jwt.getClaim("email")).isEqualTo(email);
    assertThat((String) jwt.getClaim("token_type")).isEqualTo("access");
    assertThat(jwt.getIssuedAt()).isNotNull();
    assertThat(jwt.getExpiresAt()).isAfter(Instant.now());

    UUID refreshTokenId = UUID.randomUUID();
    String refreshToken = service.generateRefreshToken(userId, refreshTokenId);

    Jwt decodedRefreshJwt = decoder.decode(refreshToken);
    assertThat(decodedRefreshJwt.getHeaders()).containsEntry("kid", keys.kid());

    JwtTokenService.RefreshTokenJwtClaims decoded = service.decodeRefreshToken(refreshToken);
    assertThat(decoded.userId()).isEqualTo(userId);
    assertThat(decoded.refreshTokenId()).isEqualTo(refreshTokenId);
  }

  @Test
  void decodeRefreshToken_rejectsNonRefreshTokens() {
    JwtProperties props = new JwtProperties();
    props.setAccessTokenTtlSeconds(60);
    props.setRefreshTokenTtlSeconds(3600);
    props.setPrivateKeyPath("");
    props.setPublicKeyPath("");

    JwtKeyProvider keyProvider = new JwtKeyProvider(props);
    JwtKeyProvider.JwtRsaKeys keys = keyProvider.loadOrGenerateKeys();

    RSAKey rsaKey =
        new RSAKey.Builder(keys.publicKey())
            .privateKey(keys.privateKey())
            .keyID(keys.kid())
            .build();

    JWKSet jwkSet = new JWKSet(rsaKey);
    JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);

    JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
    JwtDecoder decoder = new JwtKeySetDecoder(keyProvider);

    JwtTokenService service = new JwtTokenService(encoder, decoder, props, keyProvider);

    UUID userId = UUID.randomUUID();
    String accessToken = service.generateAccessToken(userId, "test@example.com");

    assertThatThrownBy(() -> service.decodeRefreshToken(accessToken))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void decodeRefreshToken_rejectsUnknownKid() {
    JwtProperties props = new JwtProperties();
    props.setAccessTokenTtlSeconds(60);
    props.setRefreshTokenTtlSeconds(3600);

    JwtKeyProvider keyProvider = new JwtKeyProvider(props);
    JwtKeyProvider.JwtRsaKeys keys = keyProvider.loadOrGenerateKeys();
    RSAKey rsaKey =
        new RSAKey.Builder(keys.publicKey()).privateKey(keys.privateKey()).keyID("unknown").build();
    JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));

    UUID userId = UUID.randomUUID();
    UUID refreshTokenId = UUID.randomUUID();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256).keyId("unknown").build(),
                    JwtClaimsSet.builder()
                        .subject(userId.toString())
                        .claim("jti", refreshTokenId.toString())
                        .claim("token_type", "refresh")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .build()))
            .getTokenValue();

    JwtTokenService service =
        new JwtTokenService(encoder, new JwtKeySetDecoder(keyProvider), props, keyProvider);

    assertThatThrownBy(() -> service.decodeRefreshToken(token))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }
}
