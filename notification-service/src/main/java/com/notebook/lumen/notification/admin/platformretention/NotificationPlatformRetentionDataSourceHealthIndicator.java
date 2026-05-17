package com.notebook.lumen.notification.admin.platformretention;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("notificationRetentionDataSourceHealth")
public class NotificationPlatformRetentionDataSourceHealthIndicator implements HealthIndicator {

  private final NotificationPlatformRetentionDataSourceProperties properties;
  private final ObjectProvider<DataSource> retentionDataSource;

  public NotificationPlatformRetentionDataSourceHealthIndicator(
      NotificationPlatformRetentionDataSourceProperties properties,
      @Qualifier(NotificationPlatformRetentionJdbcTemplateConfig.RETENTION_DATA_SOURCE_BEAN)
          ObjectProvider<DataSource> retentionDataSource) {
    this.properties = properties;
    this.retentionDataSource = retentionDataSource;
  }

  @Override
  public Health health() {
    NotificationPlatformRetentionDataSourceDiagnostics.Snapshot snapshot =
        NotificationPlatformRetentionDataSourceDiagnostics.evaluate(
            properties, retentionDataSource.getIfAvailable());
    return NotificationPlatformRetentionDataSourceDiagnostics.toHealth(snapshot);
  }
}
