package com.notebook.lumen.search.provider;

import com.notebook.lumen.search.shared.config.SearchProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("searchProviderHealth")
public class SearchProviderHealthIndicator implements HealthIndicator {
  private final SearchProviderRouter router;
  private final SearchProperties properties;

  public SearchProviderHealthIndicator(SearchProviderRouter router, SearchProperties properties) {
    this.router = router;
    this.properties = properties;
  }

  @Override
  public Health health() {
    SearchProviderType selected = router.selectedType();
    boolean selectedHealthy = router.health(selected);
    boolean postgresHealthy = router.health(SearchProviderType.POSTGRES);
    Health.Builder builder =
        selectedHealthy
                || (selected == SearchProviderType.OPENSEARCH
                    && properties.fallbackToPostgres()
                    && postgresHealthy)
            ? Health.up()
            : Health.down();
    return builder
        .withDetail("provider", selected.name().toLowerCase())
        .withDetail("postgres", postgresHealthy)
        .withDetail("opensearch", router.health(SearchProviderType.OPENSEARCH))
        .withDetail("fallbackToPostgres", properties.fallbackToPostgres())
        .withDetail("dualWriteEnabled", properties.dualWriteEnabled())
        .build();
  }
}
