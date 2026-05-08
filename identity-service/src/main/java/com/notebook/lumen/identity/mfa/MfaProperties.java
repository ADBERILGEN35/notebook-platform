package com.notebook.lumen.identity.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.mfa")
public record MfaProperties(
    boolean enabled,
    boolean webauthnEnabled,
    int challengeTtlSeconds,
    boolean requiredForPlatformAdmin) {}
