package com.notebook.lumen.content.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content.internal.admin-status")
public record InternalAdminStatusProperties(boolean enabled) {}
