package com.notebook.lumen.identity.shared.security;

import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public class JwtKeySetDecoder implements JwtDecoder {
  private final JwtKeyProvider keyProvider;

  public JwtKeySetDecoder(JwtKeyProvider keyProvider) {
    this.keyProvider = keyProvider;
  }

  @Override
  public Jwt decode(String token) throws JwtException {
    String kid = readKid(token);
    RSAPublicKey publicKey =
        keyProvider.publicKey(kid).orElseThrow(() -> new JwtException("Unknown JWT kid: " + kid));
    return NimbusJwtDecoder.withPublicKey(publicKey).build().decode(token);
  }

  private String readKid(String token) {
    try {
      String kid = SignedJWT.parse(token).getHeader().getKeyID();
      if (kid == null || kid.isBlank()) {
        throw new JwtException("JWT kid header is required");
      }
      return kid;
    } catch (java.text.ParseException e) {
      throw new JwtException("Invalid JWT", e);
    }
  }
}
