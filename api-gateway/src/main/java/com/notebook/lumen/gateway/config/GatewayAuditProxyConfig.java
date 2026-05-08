package com.notebook.lumen.gateway.config;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayAuditProxyConfig {

  @Bean
  ServiceJwtSigner auditServiceJwtSigner(GatewayAuditProxyProperties properties) {
    return new ServiceJwtSigner(properties.signerProperties());
  }
}
