package com.notebook.lumen.identity.admin.rbac.overrides;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Optional background poll for mounted manifest changes. Disabled by default; prefer manual reload
 * for predictable operations.
 */
@Component
@ConditionalOnProperty(
    prefix = "identity.admin.rbac.overrides",
    name = "watch-enabled",
    havingValue = "true")
public class AdminRbacOverridesFileWatcher {

  private final AdminRbacOverridesProperties props;
  private final AdminRbacOverrideLoader loader;
  private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

  public AdminRbacOverridesFileWatcher(
      AdminRbacOverridesProperties props, AdminRbacOverrideLoader loader) {
    this.props = props;
    this.loader = loader;
  }

  @PostConstruct
  void start() {
    scheduler.setPoolSize(1);
    scheduler.setThreadNamePrefix("admin-rbac-overrides-watch-");
    scheduler.initialize();
    long ms = Math.max(5000L, (long) props.watchIntervalSeconds() * 1000L);
    scheduler.scheduleWithFixedDelay(loader::maybeWatchReload, ms);
  }

  @PreDestroy
  void stop() {
    scheduler.shutdown();
  }
}
