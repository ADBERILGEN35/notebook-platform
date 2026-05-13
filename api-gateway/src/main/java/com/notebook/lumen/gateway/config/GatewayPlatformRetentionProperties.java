package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.admin.platform-retention")
public record GatewayPlatformRetentionProperties(boolean enabled, boolean legalHoldEnabled) {}
