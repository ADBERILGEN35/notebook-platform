package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.rate-limit")
public record GatewayRateLimitProperties(Bucket auth, Bucket protectedApi) {
  public record Bucket(int replenishRate, int burstCapacity, int requestedTokens) {}
}
