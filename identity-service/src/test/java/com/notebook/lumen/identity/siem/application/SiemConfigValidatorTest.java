package com.notebook.lumen.identity.siem.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.identity.siem.SiemProperties;
import org.junit.jupiter.api.Test;

class SiemConfigValidatorTest {

  @Test
  void genericHttpRequiresEndpoint() {
    SiemConfigValidator validator =
        new SiemConfigValidator(
            new SiemProperties(
                true,
                "generic-http",
                "",
                "none",
                "",
                "",
                "",
                10,
                100,
                10,
                30,
                3600,
                false,
                30,
                30,
                90,
                false),
            "test");
    assertThatThrownBy(validator::validate).hasMessageContaining("INVALID_SIEM_CONFIG");
  }

  @Test
  void noopDoesNotRequireEndpoint() {
    SiemConfigValidator validator =
        new SiemConfigValidator(
            new SiemProperties(
                true, "noop", "", "none", "", "", "", 10, 100, 10, 30, 3600, false, 30, 30, 90,
                false),
            "test");
    assertThatCode(validator::validate).doesNotThrowAnyException();
  }
}
