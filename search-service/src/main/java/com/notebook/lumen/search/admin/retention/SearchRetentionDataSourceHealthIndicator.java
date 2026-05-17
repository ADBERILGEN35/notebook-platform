package com.notebook.lumen.search.admin.retention;

import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("searchRetentionDataSourceHealth")
public class SearchRetentionDataSourceHealthIndicator implements HealthIndicator {

  private final SearchRetentionDataSourceProperties properties;
  private final ObjectProvider<DataSource> retentionDataSource;

  public SearchRetentionDataSourceHealthIndicator(
      SearchRetentionDataSourceProperties properties,
      @Qualifier(SearchRetentionJdbcTemplateConfig.RETENTION_DATA_SOURCE_BEAN)
          ObjectProvider<DataSource> retentionDataSource) {
    this.properties = properties;
    this.retentionDataSource = retentionDataSource;
  }

  @Override
  public Health health() {
    SearchRetentionDataSourceDiagnostics.Snapshot snapshot =
        SearchRetentionDataSourceDiagnostics.evaluate(
            properties, retentionDataSource.getIfAvailable());
    return SearchRetentionDataSourceDiagnostics.toHealth(snapshot);
  }
}
