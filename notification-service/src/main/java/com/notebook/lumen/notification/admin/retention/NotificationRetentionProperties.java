package com.notebook.lumen.notification.admin.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.retention")
public record NotificationRetentionProperties(
    boolean workerEnabled,
    boolean dryRunOnly,
    boolean manualRunEnabled,
    long pollIntervalMs,
    int batchSize,
    int maxDeletePerRun,
    int deadLetterRequeueRequestRetentionDays,
    int digestSentRetentionDays,
    int emailTerminalRetentionDays) {

  public NotificationRetentionProperties {
    if (pollIntervalMs <= 0) {
      pollIntervalMs = 86_400_000L;
    }
    if (batchSize <= 0) {
      batchSize = 1000;
    }
    if (maxDeletePerRun <= 0) {
      maxDeletePerRun = 10_000;
    }
    if (deadLetterRequeueRequestRetentionDays <= 0) {
      deadLetterRequeueRequestRetentionDays = 90;
    }
    if (digestSentRetentionDays <= 0) {
      digestSentRetentionDays = 90;
    }
    if (emailTerminalRetentionDays <= 0) {
      emailTerminalRetentionDays = 90;
    }
  }
}
