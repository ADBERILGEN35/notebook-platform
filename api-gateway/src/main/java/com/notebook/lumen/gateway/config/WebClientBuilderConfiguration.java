package com.notebook.lumen.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Spring Cloud Gateway does not always register {@link WebClient.Builder} or Jackson's {@link
 * ObjectMapper}; admin proxy services require both for outbound calls and JSON bodies.
 */
@Configuration
public class WebClientBuilderConfiguration {

  @Bean
  @ConditionalOnMissingBean(WebClient.Builder.class)
  public WebClient.Builder webClientBuilder() {
    return WebClient.builder();
  }

  @Bean
  @ConditionalOnMissingBean(ObjectMapper.class)
  public ObjectMapper objectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }
}
