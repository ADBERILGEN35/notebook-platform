package com.notebook.lumen.notification.admin.legalhold;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.internal.admin-legal-hold")
public record InternalLegalHoldAdminProperties(boolean enabled) {}
