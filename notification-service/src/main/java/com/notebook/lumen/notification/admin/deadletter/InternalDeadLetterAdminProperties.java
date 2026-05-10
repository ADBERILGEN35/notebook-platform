package com.notebook.lumen.notification.admin.deadletter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.internal.admin-dead-letter")
public record InternalDeadLetterAdminProperties(boolean enabled) {}
