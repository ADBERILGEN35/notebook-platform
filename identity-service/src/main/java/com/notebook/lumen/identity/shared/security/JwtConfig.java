package com.notebook.lumen.identity.shared.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.notebook.lumen.identity.shared.config.JwtProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

  @Bean
  public JwtKeyProvider jwtKeyProvider(JwtProperties props) {
    return new JwtKeyProvider(props);
  }

  @Bean
  public JwtEncoder jwtEncoder(JwtKeyProvider keyProvider) {
    JwtKeyProvider.JwtRsaKeySet keySet = keyProvider.loadOrGenerateKeySet();

    List<JWK> rsaKeys =
        keySet.keys().stream()
            .<JWK>map(
                keys ->
                    new RSAKey.Builder(keys.publicKey())
                        .privateKey(keys.privateKey())
                        .keyID(keys.kid())
                        .build())
            .toList();

    JWKSet jwkSet = new JWKSet(rsaKeys);
    JWKSource<com.nimbusds.jose.proc.SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);

    return new NimbusJwtEncoder(jwkSource);
  }

  @Bean
  public JwtDecoder jwtDecoder(JwtKeyProvider keyProvider) {
    return new JwtKeySetDecoder(keyProvider);
  }
}
