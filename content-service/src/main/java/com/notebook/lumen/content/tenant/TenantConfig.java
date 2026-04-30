package com.notebook.lumen.content.tenant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantConfig {
  @Bean
  TenantContext tenantContext() {
    return new TenantContext();
  }
}
