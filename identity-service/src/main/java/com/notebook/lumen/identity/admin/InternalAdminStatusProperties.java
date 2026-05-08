package com.notebook.lumen.identity.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity.internal.admin-status")
public record InternalAdminStatusProperties(boolean enabled) {}
