package com.notebook.lumen.gateway.ratelimit;

import com.notebook.lumen.gateway.config.GatewayRateLimitProperties;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RateLimitConfig {

  @Bean
  RedisRateLimiter authRedisRateLimiter(GatewayRateLimitProperties properties) {
    GatewayRateLimitProperties.Bucket auth = properties.auth();
    return new RedisRateLimiter(auth.replenishRate(), auth.burstCapacity(), auth.requestedTokens());
  }

  @Bean
  @Primary
  RedisRateLimiter protectedRedisRateLimiter(GatewayRateLimitProperties properties) {
    GatewayRateLimitProperties.Bucket protectedApi = properties.protectedApi();
    return new RedisRateLimiter(
        protectedApi.replenishRate(), protectedApi.burstCapacity(), protectedApi.requestedTokens());
  }
}
