package com.notebook.lumen.notification.admin.platformretention;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import org.springframework.boot.health.contributor.Health;

final class NotificationPlatformRetentionDataSourceDiagnostics {

  static final String WARNING_DISABLED = "RETENTION_DATASOURCE_DISABLED";
  static final String WARNING_NOT_CONFIGURED = "RETENTION_DATASOURCE_NOT_CONFIGURED";
  static final String WARNING_CONNECTION_FAILED = "RETENTION_DATASOURCE_CONNECTION_FAILED";
  static final String WARNING_USING_PRIMARY_FALLBACK =
      "RETENTION_DATASOURCE_USING_PRIMARY_FALLBACK";

  private NotificationPlatformRetentionDataSourceDiagnostics() {}

  record Snapshot(
      boolean retentionDatasourceEnabled,
      boolean configComplete,
      boolean usingDedicatedDatasource,
      boolean fallbackToPrimary,
      boolean poolConfigured,
      String lastCheckStatus,
      List<String> warningCodes) {}

  static Snapshot evaluate(
      NotificationPlatformRetentionDataSourceProperties properties,
      javax.sql.DataSource retentionDataSource) {
    if (!properties.enabled()) {
      return new Snapshot(false, false, false, true, false, "DISABLED", List.of(WARNING_DISABLED));
    }
    if (!properties.configComplete()) {
      return new Snapshot(
          true, false, false, true, false, "NOT_CONFIGURED", List.of(WARNING_NOT_CONFIGURED));
    }
    if (retentionDataSource == null) {
      return new Snapshot(
          true,
          true,
          false,
          true,
          false,
          "FALLBACK_PRIMARY",
          List.of(WARNING_USING_PRIMARY_FALLBACK));
    }
    try (Connection connection = retentionDataSource.getConnection()) {
      if (connection != null && connection.isValid(2)) {
        return new Snapshot(true, true, true, false, true, "UP", List.of());
      }
    } catch (SQLException ignored) {
      // Symbolic warning only — no exception message in health output.
    }
    return new Snapshot(true, true, true, false, true, "DOWN", List.of(WARNING_CONNECTION_FAILED));
  }

  static Health toHealth(Snapshot snapshot) {
    Health.Builder builder =
        switch (snapshot.lastCheckStatus()) {
          case "UP" -> Health.up();
          case "DOWN" -> Health.down();
          case "DISABLED", "NOT_CONFIGURED", "FALLBACK_PRIMARY" -> Health.up();
          default -> Health.unknown();
        };
    return builder
        .withDetail("retentionDatasourceEnabled", snapshot.retentionDatasourceEnabled())
        .withDetail("configComplete", snapshot.configComplete())
        .withDetail("usingDedicatedDatasource", snapshot.usingDedicatedDatasource())
        .withDetail("fallbackToPrimary", snapshot.fallbackToPrimary())
        .withDetail("poolConfigured", snapshot.poolConfigured())
        .withDetail("lastCheckStatus", snapshot.lastCheckStatus())
        .withDetail("warningCodes", snapshot.warningCodes())
        .build();
  }
}
