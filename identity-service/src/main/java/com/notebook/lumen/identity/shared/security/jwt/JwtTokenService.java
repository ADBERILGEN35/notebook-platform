package com.notebook.lumen.identity.shared.security.jwt;

import com.notebook.lumen.identity.shared.config.JwtProperties;
import com.notebook.lumen.identity.shared.exception.InvalidRefreshTokenException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private static final String TOKEN_TYPE_CLAIM = "token_type";
  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final String REFRESH_TOKEN_TYPE = "refresh";
  private static final String EMAIL_CLAIM = "email";
  private static final String JTI_CLAIM = "jti";

  private final JwtEncoder jwtEncoder;
  private final JwtDecoder jwtDecoder;
  private final JwtProperties jwtProperties;
  private final com.notebook.lumen.identity.shared.security.JwtKeyProvider keyProvider;

  public JwtTokenService(
      JwtEncoder jwtEncoder,
      JwtDecoder jwtDecoder,
      JwtProperties jwtProperties,
      com.notebook.lumen.identity.shared.security.JwtKeyProvider keyProvider) {
    this.jwtEncoder = jwtEncoder;
    this.jwtDecoder = jwtDecoder;
    this.jwtProperties = jwtProperties;
    this.keyProvider = keyProvider;
  }

  public String generateAccessToken(UUID userId, String email) {
    Instant now = Instant.now();
    long ttlSeconds = jwtProperties.getAccessTokenTtlSeconds();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim(EMAIL_CLAIM, email)
            .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttlSeconds))
            .build();

    return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader(), claims)).getTokenValue();
  }

  public String generateRefreshToken(UUID userId, UUID refreshTokenId) {
    Instant now = Instant.now();
    long ttlSeconds = jwtProperties.getRefreshTokenTtlSeconds();

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(userId.toString())
            .claim(JTI_CLAIM, refreshTokenId.toString())
            .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(ttlSeconds))
            .build();

    return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader(), claims)).getTokenValue();
  }

  public RefreshTokenJwtClaims decodeRefreshToken(String refreshToken) {
    try {
      Jwt jwt = jwtDecoder.decode(refreshToken);
      Map<String, Object> claims = jwt.getClaims();

      Object tokenTypeObj = claims.get(TOKEN_TYPE_CLAIM);
      if (!(tokenTypeObj instanceof String tokenType) || !REFRESH_TOKEN_TYPE.equals(tokenType)) {
        throw new InvalidRefreshTokenException();
      }

      String jti = (String) claims.get(JTI_CLAIM);
      if (jti == null) {
        throw new InvalidRefreshTokenException();
      }

      UUID userId = UUID.fromString(jwt.getSubject());
      UUID refreshTokenId = UUID.fromString(jti);

      Instant expiresAt = jwt.getExpiresAt();
      return new RefreshTokenJwtClaims(userId, refreshTokenId, expiresAt);
    } catch (JwtException | IllegalArgumentException e) {
      throw new InvalidRefreshTokenException();
    }
  }

  public long accessTokenTtlSeconds() {
    return jwtProperties.getAccessTokenTtlSeconds();
  }

  public long refreshTokenTtlSeconds() {
    return jwtProperties.getRefreshTokenTtlSeconds();
  }

  private JwsHeader jwsHeader() {
    return JwsHeader.with(SignatureAlgorithm.RS256)
        .keyId(keyProvider.loadOrGenerateKeySet().activeKid())
        .build();
  }

  public record RefreshTokenJwtClaims(UUID userId, UUID refreshTokenId, Instant expiresAt) {}
}
