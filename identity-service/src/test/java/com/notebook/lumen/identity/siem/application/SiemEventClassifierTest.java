package com.notebook.lumen.identity.siem.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.identity.siem.SiemProperties;
import org.junit.jupiter.api.Test;

class SiemEventClassifierTest {

  @Test
  void classifiesHighValueEvents() {
    SiemEventClassifier classifier =
        new SiemEventClassifier(
            new SiemProperties(
                true, "noop", "", "none", "", "", "", 10, 100, 10, 30, 3600, false, 30, 30, 90, false));
    assertThat(classifier.classify("USER_LOGIN_FAILED")).isPresent();
    assertThat(classifier.classify("SCIM_USER_DEPROVISIONED")).isPresent();
  }

  @Test
  void excludesLoginSuccessByDefault() {
    SiemEventClassifier classifier =
        new SiemEventClassifier(
            new SiemProperties(
                true, "noop", "", "none", "", "", "", 10, 100, 10, 30, 3600, false, 30, 30, 90, false));
    assertThat(classifier.classify("USER_LOGIN_SUCCEEDED")).isEmpty();
  }
}
