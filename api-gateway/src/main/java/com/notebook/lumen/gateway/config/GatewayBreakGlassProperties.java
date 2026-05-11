package com.notebook.lumen.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "gateway.break-glass")
public record GatewayBreakGlassProperties(
    @DefaultValue("false") boolean adminAllowed,
    @DefaultValue("false") boolean allowAdminWrite) {}

