package com.notebook.lumen.content.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TenantContextTest {
  @Test
  void setGetAndClearTenantContext() {
    TenantContext context = new TenantContext();
    UUID workspaceId = UUID.randomUUID();

    context.set(workspaceId);

    assertThat(context.getRequired()).isEqualTo(workspaceId);
    assertThat(context.getOptional()).contains(workspaceId);

    context.clear();

    assertThat(context.getOptional()).isEmpty();
    assertThatThrownBy(context::getRequired).isInstanceOf(IllegalStateException.class);
  }
}
