package com.notebook.lumen.identity.breakglass;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BreakGlassDenylistCleanupJob {
  private static final Logger log = LoggerFactory.getLogger(BreakGlassDenylistCleanupJob.class);

  private final BreakGlassTokenDenylistRepository denylistRepository;
  private final BreakGlassProperties props;

  public BreakGlassDenylistCleanupJob(
      BreakGlassTokenDenylistRepository denylistRepository, BreakGlassProperties props) {
    this.denylistRepository = denylistRepository;
    this.props = props;
  }

  @Scheduled(fixedDelayString = "${identity.break-glass.revocation-cleanup-interval-ms:3600000}")
  @Transactional
  public void cleanupExpiredDenylistEntries() {
    if (!props.revocationCleanupEnabled()) {
      return;
    }
    Instant threshold =
        Instant.now().minus(Math.max(1, props.denylistRetentionHours()), ChronoUnit.HOURS);
    long removed = denylistRepository.deleteByExpiresAtBefore(threshold);
    if (removed > 0) {
      log.info("break_glass_denylist_cleanup_removed count={}", removed);
    }
  }
}
