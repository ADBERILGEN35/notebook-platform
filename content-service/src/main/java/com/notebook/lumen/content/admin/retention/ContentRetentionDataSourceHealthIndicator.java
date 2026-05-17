package com.notebook.lumen.content.admin.retention;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Actuator health for dedicated retention datasource (Faz 113). Exposed at {@code /actuator/health}
 * as component {@code contentRetentionDataSourceHealth}. Safe details only.
 */
@Component("contentRetentionDataSourceHealth")
public class ContentRetentionDataSourceHealthIndicator implements HealthIndicator {

  private final ContentRetentionDataSourceProperties properties;
  private final ObjectProvider<DataSource> retentionDataSource;

  public ContentRetentionDataSourceHealthIndicator(
      ContentRetentionDataSourceProperties properties,
      @Qualifier(ContentRetentionJdbcTemplateConfig.RETENTION_DATA_SOURCE_BEAN)
          ObjectProvider<DataSource> retentionDataSource) {
    this.properties = properties;
    this.retentionDataSource = retentionDataSource;
  }

  @Override
  public Health health() {
    ContentRetentionDataSourceDiagnostics.Snapshot snapshot =
        ContentRetentionDataSourceDiagnostics.evaluate(
            properties, retentionDataSource.getIfAvailable());
    return ContentRetentionDataSourceDiagnostics.toHealth(snapshot);
  }
}
