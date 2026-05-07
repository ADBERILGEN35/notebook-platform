package com.notebook.lumen.notification.email.webhook;

public record EmailWebhookVerificationResult(boolean verified, boolean replayRejected) {
  public static EmailWebhookVerificationResult accepted() {
    return new EmailWebhookVerificationResult(true, false);
  }

  public static EmailWebhookVerificationResult rejected() {
    return new EmailWebhookVerificationResult(false, false);
  }

  public static EmailWebhookVerificationResult rejectedAsReplay() {
    return new EmailWebhookVerificationResult(false, true);
  }
}
