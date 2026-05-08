package com.notebook.lumen.identity.siem.application;

import com.notebook.lumen.identity.siem.SiemProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SiemConfigValidator {
  private static final Logger log = LoggerFactory.getLogger(SiemConfigValidator.class);
  private final SiemProperties properties;
  private final String activeProfiles;

  public SiemConfigValidator(
      SiemProperties properties, @Value("${spring.profiles.active:default}") String activeProfiles) {
    this.properties = properties;
    this.activeProfiles = activeProfiles;
  }

  @PostConstruct
  public void validate() {
    if (!properties.pushEnabled()) {
      return;
    }
    if ("generic-http".equals(properties.effectiveProvider())
        && (properties.endpointUrl() == null || properties.endpointUrl().isBlank())) {
      throw new IllegalStateException("INVALID_SIEM_CONFIG: endpoint required for generic-http provider");
    }
    if ("generic-http".equals(properties.effectiveProvider())
        && properties.endpointUrl() != null
        && properties.endpointUrl().startsWith("http://")) {
      if (activeProfiles.contains("prod")) {
        throw new IllegalStateException("INVALID_SIEM_CONFIG: non-TLS SIEM endpoint is not allowed in prod");
      }
      log.warn("siem_non_tls_endpoint_configured endpoint={}", properties.endpointUrl());
    }
    if ("bearer".equals(properties.effectiveAuthMode())
        && (properties.bearerToken() == null || properties.bearerToken().isBlank())) {
      throw new IllegalStateException("INVALID_SIEM_CONFIG: bearer token required");
    }
    if ("header".equals(properties.effectiveAuthMode())
        && (properties.customHeaderName() == null
            || properties.customHeaderName().isBlank()
            || properties.customHeaderValue() == null
            || properties.customHeaderValue().isBlank())) {
      throw new IllegalStateException("INVALID_SIEM_CONFIG: custom header name/value required");
    }
  }
}
