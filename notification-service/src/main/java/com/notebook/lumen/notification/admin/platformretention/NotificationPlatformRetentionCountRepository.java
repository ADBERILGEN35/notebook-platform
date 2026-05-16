package com.notebook.lumen.notification.admin.platformretention;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate-only retention count queries for notification platform retention. Every query is
 * cutoff-bounded, filters on indexed timestamp/status columns, and uses LIMIT to cap scan cost;
 * callers receive {@link CountResult} indicating whether the cap was reached. No notification body,
 * payload, subject, recipient email, or workspace identifier is read.
 *
 * <p>RLS note: count queries are admin-scope cross-workspace. The runtime role must have BYPASSRLS
 * or queries must run with {@code row_security=off}. Tests assume Testcontainers default permissive
 * role; production requires DBA setup (see retention-rls-production-runbook.md).
 */
@Repository
public class NotificationPlatformRetentionCountRepository {

  private final JdbcTemplate jdbcTemplate;

  public NotificationPlatformRetentionCountRepository(DataSource dataSource) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countAnalyticsHourlyBefore(Instant cutoff, int cap) {
    return cappedCount(
        "SELECT 1 FROM notification_delivery_analytics_hourly WHERE bucket_start < ? LIMIT ?",
        cutoff,
        cap);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countFanoutSentBefore(Instant cutoff, int cap) {
    return cappedCount(
        "SELECT 1 FROM notification_fanout_outbox WHERE status = 'SENT' AND sent_at IS NOT NULL"
            + " AND sent_at < ? LIMIT ?",
        cutoff,
        cap);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countFanoutDeadBefore(Instant cutoff, int cap) {
    return cappedCount(
        "SELECT 1 FROM notification_fanout_outbox WHERE status = 'DEAD'"
            + " AND coalesce(dead_at, created_at) < ? LIMIT ?",
        cutoff,
        cap);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countDeadLetterRequeueBefore(Instant cutoff, int cap) {
    return cappedCount(
        "SELECT 1 FROM notification_dead_letter_requeue_requests WHERE created_at < ? LIMIT ?",
        cutoff,
        cap);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countDigestTerminalBefore(Instant cutoff, int cap) {
    int safeCap = cap <= 0 ? 100_000 : cap;
    String sql =
        "SELECT count(*) FROM (SELECT 1 FROM notification_digest_items"
            + " WHERE status IN ('SENT', 'CANCELLED')"
            + " AND ((status = 'SENT' AND coalesce(sent_at, created_at) < ?)"
            + " OR (status = 'CANCELLED' AND created_at < ?)) LIMIT ?) AS bounded";
    Long result =
        jdbcTemplate.queryForObject(
            sql, Long.class, Timestamp.from(cutoff), Timestamp.from(cutoff), safeCap + 1);
    return toResult(result, safeCap);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countEmailTerminalBefore(Instant cutoff, int cap) {
    return cappedCount(
        "SELECT 1 FROM email_notifications WHERE status IN ('SENT', 'FAILED', 'CANCELLED',"
            + " 'SKIPPED') AND updated_at < ? LIMIT ?",
        cutoff,
        cap);
  }

  private CountResult cappedCount(String boundedSelect, Instant cutoff, int cap) {
    int safeCap = cap <= 0 ? 100_000 : cap;
    String sql = "SELECT count(*) FROM (" + boundedSelect + ") AS bounded";
    Long result = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(cutoff), safeCap + 1);
    return toResult(result, safeCap);
  }

  private static CountResult toResult(Long raw, int safeCap) {
    long total = raw == null ? 0 : raw;
    boolean capped = total > safeCap;
    return new CountResult(capped ? safeCap : total, capped);
  }

  public record CountResult(long count, boolean capped) {}
}
