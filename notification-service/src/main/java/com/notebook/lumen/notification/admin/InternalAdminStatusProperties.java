package com.notebook.lumen.notification.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.internal.admin-status")
public record InternalAdminStatusProperties(boolean enabled) {}
