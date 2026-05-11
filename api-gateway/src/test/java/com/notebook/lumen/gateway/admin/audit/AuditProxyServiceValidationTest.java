package com.notebook.lumen.gateway.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.gateway.error.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditProxyServiceValidationTest {

  @Test
  void normalizesValidQuery() {
    Map<String, String> normalized =
        AuditProxyService.validateAndNormalize(
            Map.of(
                "eventType", "LOGIN_SUCCESS",
                "actorUserId", "2f99e3c9-f998-4f75-a3ea-2752484cb9be",
                "page", "1",
                "size", "25",
                "sort", "createdAt,desc"));

    assertThat(normalized.get("eventType")).isEqualTo("LOGIN_SUCCESS");
    assertThat(normalized.get("actorUserId")).isEqualTo("2f99e3c9-f998-4f75-a3ea-2752484cb9be");
    assertThat(normalized.get("page")).isEqualTo("1");
    assertThat(normalized.get("size")).isEqualTo("25");
    assertThat(normalized.get("sort")).isEqualTo("createdAt,desc");
  }

  @Test
  void rejectsInvalidUuidFilter() {
    assertThatThrownBy(
            () -> AuditProxyService.validateAndNormalize(Map.of("actorUserId", "bad-uuid")))
        .isInstanceOf(AuditProxyException.class)
        .satisfies(
            throwable ->
                assertThat(((AuditProxyException) throwable).errorCode())
                    .isEqualTo(ErrorCode.INVALID_AUDIT_FILTER));
  }

  @Test
  void rejectsInvertedDateRange() {
    assertThatThrownBy(
            () ->
                AuditProxyService.validateAndNormalize(
                    Map.of(
                        "createdFrom", "2026-06-01T00:00:00Z",
                        "createdTo", "2026-05-01T00:00:00Z")))
        .isInstanceOf(AuditProxyException.class)
        .satisfies(
            throwable ->
                assertThat(((AuditProxyException) throwable).errorCode())
                    .isEqualTo(ErrorCode.INVALID_AUDIT_FILTER));
  }
}
