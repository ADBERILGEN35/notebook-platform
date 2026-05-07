package com.notebook.lumen.common.security.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkerInstanceIdsTest {
  @Test
  void usesConfiguredValueWhenProvided() {
    assertThat(WorkerInstanceIds.resolve("pod-1", "email-worker")).isEqualTo("pod-1");
  }

  @Test
  void sanitizesAndBoundsConfiguredValue() {
    String value = WorkerInstanceIds.resolve("bad value with spaces and !", "worker");

    assertThat(value).doesNotContain(" ");
    assertThat(value).doesNotContain("!");
    assertThat(value).hasSizeLessThanOrEqualTo(120);
  }
}
