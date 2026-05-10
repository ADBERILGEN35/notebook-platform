package com.notebook.lumen.notification.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.internal.admin-analytics")
public record InternalNotificationAnalyticsAdminProperties(boolean enabled) {}
