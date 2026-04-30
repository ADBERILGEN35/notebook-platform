package com.notebook.lumen.workspace.config;

import com.notebook.lumen.workspace.client.IdentityClient;
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
public class IdentityClientConfig {

  @Bean
  IdentityClient identityClient(WorkspaceProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(Duration.ofMillis(properties.identity().timeoutMs()));
    RestClient restClient =
        RestClient.builder()
            .baseUrl(properties.identity().serviceUrl())
            .requestFactory(requestFactory)
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(IdentityClient.class);
  }

  @Bean
  CircuitBreaker identityClientCircuitBreaker(WorkspaceProperties properties) {
    return CircuitBreaker.of(
        "identityLookup",
        CircuitBreakerConfig.custom()
            .failureRateThreshold(properties.identity().circuitBreakerFailureThreshold())
            .waitDurationInOpenState(
                Duration.ofMillis(properties.identity().circuitBreakerOpenStateMs()))
            .build());
  }

  @Bean
  Retry identityClientRetry(WorkspaceProperties properties) {
    return Retry.of(
        "identityLookup",
        RetryConfig.custom()
            .maxAttempts(properties.identity().retryMaxAttempts())
            .waitDuration(Duration.ofMillis(100))
            .build());
  }
}
