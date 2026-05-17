package com.notebook.lumen.notification;

import com.notebook.lumen.notification.admin.InternalAdminStatusProperties;
import com.notebook.lumen.notification.admin.deadletter.InternalDeadLetterAdminProperties;
import com.notebook.lumen.notification.admin.deadletter.NotificationDeadLetterProperties;
import com.notebook.lumen.notification.admin.legalhold.InternalLegalHoldAdminProperties;
import com.notebook.lumen.notification.admin.legalhold.NotificationLegalHoldProperties;
import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionDataSourceProperties;
import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionProperties;
import com.notebook.lumen.notification.admin.retention.InternalRetentionAdminProperties;
import com.notebook.lumen.notification.admin.retention.NotificationRetentionProperties;
import com.notebook.lumen.notification.analytics.InternalNotificationAnalyticsAdminProperties;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsProperties;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
  NotificationProperties.class,
  InternalAdminStatusProperties.class,
  NotificationAnalyticsProperties.class,
  InternalNotificationAnalyticsAdminProperties.class,
  InternalDeadLetterAdminProperties.class,
  NotificationDeadLetterProperties.class,
  InternalRetentionAdminProperties.class,
  NotificationRetentionProperties.class,
  InternalLegalHoldAdminProperties.class,
  NotificationLegalHoldProperties.class,
  NotificationPlatformRetentionProperties.class,
  NotificationPlatformRetentionDataSourceProperties.class
})
public class NotificationServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
  }
}
