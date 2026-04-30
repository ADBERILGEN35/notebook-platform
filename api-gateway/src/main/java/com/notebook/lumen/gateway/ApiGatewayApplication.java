package com.notebook.lumen.gateway;

import com.notebook.lumen.gateway.config.GatewayCorsProperties;
import com.notebook.lumen.gateway.config.GatewayJwtProperties;
import com.notebook.lumen.gateway.config.GatewayRateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
  GatewayCorsProperties.class,
  GatewayJwtProperties.class,
  GatewayRateLimitProperties.class
})
public class ApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }
}
