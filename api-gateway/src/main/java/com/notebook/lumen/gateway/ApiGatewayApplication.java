package com.notebook.lumen.gateway;

import com.notebook.lumen.gateway.config.GatewayAdminEnterpriseProperties;
import com.notebook.lumen.gateway.config.GatewayAdminProperties;
import com.notebook.lumen.gateway.config.GatewayAdminRbacProperties;
import com.notebook.lumen.gateway.config.GatewayAdminRbacVisibilityProperties;
import com.notebook.lumen.gateway.config.GatewayAdminWriteProperties;
import com.notebook.lumen.gateway.config.GatewayAuditExportProperties;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.config.GatewayAuthProperties;
import com.notebook.lumen.gateway.config.GatewayBreakGlassProperties;
import com.notebook.lumen.gateway.config.GatewayContentRetentionProperties;
import com.notebook.lumen.gateway.config.GatewayCorsProperties;
import com.notebook.lumen.gateway.config.GatewayJwtProperties;
import com.notebook.lumen.gateway.config.GatewayNotificationRetentionProperties;
import com.notebook.lumen.gateway.config.GatewayPlatformRetentionProperties;
import com.notebook.lumen.gateway.config.GatewayRateLimitProperties;
import com.notebook.lumen.gateway.config.GatewaySearchRetentionProperties;
import com.notebook.lumen.gateway.config.GatewayWorkspaceRetentionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
  GatewayCorsProperties.class,
  GatewayAuthProperties.class,
  GatewayJwtProperties.class,
  GatewayRateLimitProperties.class,
  GatewayAdminProperties.class,
  GatewayAdminRbacProperties.class,
  GatewayAdminRbacVisibilityProperties.class,
  GatewayAdminEnterpriseProperties.class,
  GatewayAuditProxyProperties.class,
  GatewayAuditExportProperties.class,
  GatewayAdminWriteProperties.class,
  GatewayPlatformRetentionProperties.class,
  GatewayContentRetentionProperties.class,
  GatewayNotificationRetentionProperties.class,
  GatewaySearchRetentionProperties.class,
  GatewayWorkspaceRetentionProperties.class,
  GatewayBreakGlassProperties.class
})
public class ApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }
}
