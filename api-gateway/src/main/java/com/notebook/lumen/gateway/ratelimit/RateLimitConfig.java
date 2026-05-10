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

  @Bean
  RedisRateLimiter adminAuditRedisRateLimiter(GatewayRateLimitProperties properties) {
    GatewayRateLimitProperties.Bucket adminAudit = properties.effectiveAdminAudit();
    return new RedisRateLimiter(
        adminAudit.replenishRate(), adminAudit.burstCapacity(), adminAudit.requestedTokens());
  }

  @Bean
  RedisRateLimiter adminAuditExportRedisRateLimiter(GatewayRateLimitProperties properties) {
    GatewayRateLimitProperties.Bucket adminAuditExport = properties.effectiveAdminAuditExport();
    return new RedisRateLimiter(
        adminAuditExport.replenishRate(),
        adminAuditExport.burstCapacity(),
        adminAuditExport.requestedTokens());
  }

  @Bean
  RedisRateLimiter adminAuditExportMachineRedisRateLimiter(GatewayRateLimitProperties properties) {
    GatewayRateLimitProperties.Bucket adminAuditExportMachine =
        properties.effectiveAdminAuditExportMachine();
    return new RedisRateLimiter(
        adminAuditExportMachine.replenishRate(),
        adminAuditExportMachine.burstCapacity(),
        adminAuditExportMachine.requestedTokens());
  }

  @Bean
  RedisRateLimiter scimRedisRateLimiter(GatewayRateLimitProperties properties) {
    GatewayRateLimitProperties.Bucket scim = properties.effectiveScim();
    return new RedisRateLimiter(scim.replenishRate(), scim.burstCapacity(), scim.requestedTokens());
  }

  @Bean
  RedisRateLimiter adminWriteRedisRateLimiter(GatewayRateLimitProperties properties) {
    GatewayRateLimitProperties.Bucket adminWrite = properties.effectiveAdminWrite();
    return new RedisRateLimiter(
        adminWrite.replenishRate(), adminWrite.burstCapacity(), adminWrite.requestedTokens());
  }
}
