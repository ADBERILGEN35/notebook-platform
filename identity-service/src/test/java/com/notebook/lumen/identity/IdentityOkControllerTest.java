package com.notebook.lumen.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityOkControllerTest {

  @Test
  void okEndpoint_returnsOk() {
    IdentityOkController controller = new IdentityOkController();
    Map<String, Object> body = controller.ok();

    assertThat(body).containsEntry("status", "OK").containsEntry("service", "identity-service");
  }
}
