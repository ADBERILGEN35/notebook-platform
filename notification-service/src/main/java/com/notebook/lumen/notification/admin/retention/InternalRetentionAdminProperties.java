package com.notebook.lumen.notification.admin.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.internal.admin-retention")
public record InternalRetentionAdminProperties(boolean enabled) {}
