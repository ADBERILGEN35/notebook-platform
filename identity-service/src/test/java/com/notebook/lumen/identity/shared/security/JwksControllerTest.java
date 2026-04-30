package com.notebook.lumen.identity.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.identity.shared.config.JwtProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwksControllerTest {
  @Test
  void jwks_returnsPublicOnlyJwkSet() {
    JwtProperties props = new JwtProperties();
    JwtKeyProvider keyProvider = new JwtKeyProvider(props);
    JwksController controller = new JwksController(keyProvider);

    Map<String, Object> jwks = controller.jwks();

    assertThat(jwks).containsKey("keys");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
    assertThat(keys).hasSize(1);
    assertThat(keys.getFirst())
        .containsEntry("kty", "RSA")
        .containsEntry("kid", "primary")
        .containsEntry("use", "sig")
        .containsEntry("alg", "RS256")
        .containsKeys("n", "e")
        .doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
  }
}
