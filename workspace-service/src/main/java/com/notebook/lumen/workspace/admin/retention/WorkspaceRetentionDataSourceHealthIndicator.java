package com.notebook.lumen.workspace.admin.retention;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("workspaceRetentionDataSourceHealth")
public class WorkspaceRetentionDataSourceHealthIndicator implements HealthIndicator {

  private final WorkspaceRetentionDataSourceProperties properties;
  private final ObjectProvider<DataSource> retentionDataSource;

  public WorkspaceRetentionDataSourceHealthIndicator(
      WorkspaceRetentionDataSourceProperties properties,
      @Qualifier(WorkspaceRetentionJdbcTemplateConfig.RETENTION_DATA_SOURCE_BEAN)
          ObjectProvider<DataSource> retentionDataSource) {
    this.properties = properties;
    this.retentionDataSource = retentionDataSource;
  }

  @Override
  public Health health() {
    WorkspaceRetentionDataSourceDiagnostics.Snapshot snapshot =
        WorkspaceRetentionDataSourceDiagnostics.evaluate(
            properties, retentionDataSource.getIfAvailable());
    return WorkspaceRetentionDataSourceDiagnostics.toHealth(snapshot);
  }
}
