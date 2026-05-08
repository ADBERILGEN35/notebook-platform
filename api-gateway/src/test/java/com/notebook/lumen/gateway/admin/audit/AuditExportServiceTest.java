package com.notebook.lumen.gateway.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.gateway.config.GatewayAuditExportProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AuditExportServiceTest {

  @Test
  void rejectsMissingRange() {
    AuditProxyService proxy = mock(AuditProxyService.class);
    AuditExportService service =
        new AuditExportService(
            proxy,
            new GatewayAuditExportProperties(
                true,
                31,
                10000,
                200,
                new GatewayAuditExportProperties.MachineAuth(
                    false, "", "api-gateway", "admin:audit:export", "", "", 900),
                false,
                false,
                ""));

    assertThatThrownBy(() -> service.export(AuditSource.IDENTITY, "csv", Map.of()).block())
        .isInstanceOf(AuditProxyException.class)
        .satisfies(
            e ->
                assertThat(((AuditProxyException) e).errorCode())
                    .isEqualTo(ErrorCode.AUDIT_EXPORT_RANGE_REQUIRED));
  }

  @Test
  void csvEscapesInjectionAndMetadataSecrets() {
    AuditProxyService proxy = mock(AuditProxyService.class);
    when(proxy.proxy(any(), any()))
        .thenReturn(
            Mono.just(
                """
                {"items":[{"id":"1","eventType":"LOGIN","actorUserId":"u","workspaceId":"w","aggregateType":"USER","aggregateId":"a","requestId":"r","ipAddress":"127.0.0.1","userAgent":"@agent","metadata":{"token":"abc","safe":"ok"},"createdAt":"2026-01-01T00:00:00Z"}],"hasNext":false}
                """));

    AuditExportService service =
        new AuditExportService(
            proxy,
            new GatewayAuditExportProperties(
                true,
                31,
                10000,
                200,
                new GatewayAuditExportProperties.MachineAuth(
                    false, "", "api-gateway", "admin:audit:export", "", "", 900),
                false,
                false,
                ""));
    Map<String, String> filters = new LinkedHashMap<>();
    filters.put("createdFrom", "2026-01-01T00:00:00Z");
    filters.put("createdTo", "2026-01-02T00:00:00Z");
    filters.put("sort", "createdAt,desc");

    AuditExportService.AuditExportPayload payload =
        service.export(AuditSource.IDENTITY, "csv", filters).block();

    assertThat(payload).isNotNull();
    String csv = new String(payload.bytes());
    assertThat(csv).contains("'@agent");
    assertThat(csv).contains("***masked***");
  }

  @Test
  void jsonlSerializesRows() {
    AuditProxyService proxy = mock(AuditProxyService.class);
    when(proxy.proxy(any(), any()))
        .thenReturn(
            Mono.just(
                """
                {"items":[{"id":"1","eventType":"LOGIN","metadata":{"safe":"ok"},"createdAt":"2026-01-01T00:00:00Z"}],"hasNext":false}
                """));

    AuditExportService service =
        new AuditExportService(
            proxy,
            new GatewayAuditExportProperties(
                true,
                31,
                10000,
                200,
                new GatewayAuditExportProperties.MachineAuth(
                    false, "", "api-gateway", "admin:audit:export", "", "", 900),
                false,
                false,
                ""));
    Map<String, String> filters = new LinkedHashMap<>();
    filters.put("createdFrom", "2026-01-01T00:00:00Z");
    filters.put("createdTo", "2026-01-02T00:00:00Z");

    AuditExportService.AuditExportPayload payload =
        service.export(AuditSource.CONTENT, "jsonl", filters).block();
    assertThat(payload.contentType()).isEqualTo("application/x-ndjson");
    assertThat(new String(payload.bytes())).contains("\"source\":\"content\"");
  }
}
