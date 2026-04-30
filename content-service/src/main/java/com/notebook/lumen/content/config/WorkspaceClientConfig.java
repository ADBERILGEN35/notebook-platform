package com.notebook.lumen.content.config;

import com.notebook.lumen.common.security.secrets.SecretValue;
import com.notebook.lumen.content.client.WorkspaceClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class WorkspaceClientConfig {
  @Bean
  WorkspaceClient workspaceClient(ContentProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(Duration.ofMillis(properties.workspace().timeoutMs()));
    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl(properties.workspace().serviceUrl())
            .requestFactory(requestFactory);
    SecretValue internalToken = properties.workspace().effectiveInternalApiTokenSecret();
    if (internalToken.hasText()) {
      builder.defaultHeader("X-Internal-Token", internalToken.value());
    }
    RestClient restClient = builder.build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(WorkspaceClient.class);
  }

  @Bean
  CircuitBreaker workspacePermissionCircuitBreaker(ContentProperties properties) {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(properties.workspace().circuitBreakerFailureThreshold())
            .waitDurationInOpenState(
                Duration.ofMillis(properties.workspace().circuitBreakerOpenStateMs()))
            .build();
    return CircuitBreaker.of("workspacePermission", config);
  }

  @Bean
  Retry workspacePermissionRetry(ContentProperties properties) {
    RetryConfig config =
        RetryConfig.custom()
            .maxAttempts(properties.workspace().retryMaxAttempts())
            .waitDuration(Duration.ofMillis(100))
            .build();
    return Retry.of("workspacePermission", config);
  }
}
