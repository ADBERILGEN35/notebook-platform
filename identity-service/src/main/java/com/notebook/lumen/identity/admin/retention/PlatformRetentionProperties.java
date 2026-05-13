package com.notebook.lumen.identity.admin.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.platform-retention")
public record PlatformRetentionProperties(boolean enabled, boolean legalHoldEnabled) {}
