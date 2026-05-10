package com.notebook.lumen.notification.analytics;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class NotificationWorkerRunTimestamps {
  private final AtomicReference<Instant> fanoutLastRun = new AtomicReference<>();
  private final AtomicReference<Instant> digestLastRun = new AtomicReference<>();
  private final AtomicReference<Instant> emailLastRun = new AtomicReference<>();

  public void markFanoutRun(Instant t) {
    fanoutLastRun.set(t);
  }

  public void markDigestRun(Instant t) {
    digestLastRun.set(t);
  }

  public void markEmailRun(Instant t) {
    emailLastRun.set(t);
  }

  public Instant fanoutLastRun() {
    return fanoutLastRun.get();
  }

  public Instant digestLastRun() {
    return digestLastRun.get();
  }

  public Instant emailLastRun() {
    return emailLastRun.get();
  }
}
