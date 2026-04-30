package com.notebook.lumen.identity.shared.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {
  private final JwtKeyProvider keyProvider;

  public JwksController(JwtKeyProvider keyProvider) {
    this.keyProvider = keyProvider;
  }

  @GetMapping("/.well-known/jwks.json")
  public Map<String, Object> jwks() {
    List<JWK> publicKeys =
        keyProvider.loadOrGenerateKeySet().keys().stream()
            .<JWK>map(
                key ->
                    new RSAKey.Builder(key.publicKey())
                        .keyID(key.kid())
                        .keyUse(com.nimbusds.jose.jwk.KeyUse.SIGNATURE)
                        .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                        .build())
            .toList();
    return new JWKSet(publicKeys).toJSONObject(false);
  }
}
