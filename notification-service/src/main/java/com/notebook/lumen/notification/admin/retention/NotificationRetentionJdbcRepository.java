package com.notebook.lumen.notification.admin.retention;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRetentionJdbcRepository {

  public record CountMin(long count, Instant oldestEligibleAt) {}

  private static final RowMapper<CountMin> COUNT_MIN =
      (rs, rowNum) ->
          new CountMin(rs.getLong("c"), readInstant(rs, "m"));

  private final JdbcTemplate jdbc;

  public NotificationRetentionJdbcRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public CountMin countAnalyticsHourlyEligible(Instant cutoff) {
    return jdbc.queryForObject(
        """
        select count(*) as c, min(bucket_start) as m
        from notification_delivery_analytics_hourly
        where bucket_start < ?
        """,
        COUNT_MIN,
        cutoff);
  }

  public int deleteAnalyticsHourlyBatch(Instant cutoff, int limit) {
    return jdbc.update(
        """
        delete from notification_delivery_analytics_hourly
        where id in (
          select id from notification_delivery_analytics_hourly
          where bucket_start < ?
          order by bucket_start asc
          limit ?
        )
        """,
        cutoff,
        limit);
  }

  public CountMin countFanoutSentEligible(Instant cutoff) {
    return jdbc.queryForObject(
        """
        select count(*) as c, min(sent_at) as m
        from notification_fanout_outbox
        where status = 'SENT' and sent_at is not null and sent_at < ?
        """,
        COUNT_MIN,
        cutoff);
  }

  public int deleteFanoutSentBatch(Instant cutoff, int limit) {
    return jdbc.update(
        """
        delete from notification_fanout_outbox
        where id in (
          select id from notification_fanout_outbox
          where status = 'SENT' and sent_at is not null and sent_at < ?
          order by sent_at asc
          limit ?
        )
        """,
        cutoff,
        limit);
  }

  public CountMin countFanoutDeadEligible(Instant cutoff) {
    return jdbc.queryForObject(
        """
        select count(*) as c, min(coalesce(dead_at, created_at)) as m
        from notification_fanout_outbox
        where status = 'DEAD' and coalesce(dead_at, created_at) < ?
        """,
        COUNT_MIN,
        cutoff);
  }

  public int deleteFanoutDeadBatch(Instant cutoff, int limit) {
    return jdbc.update(
        """
        delete from notification_fanout_outbox
        where id in (
          select id from notification_fanout_outbox
          where status = 'DEAD' and coalesce(dead_at, created_at) < ?
          order by coalesce(dead_at, created_at) asc
          limit ?
        )
        """,
        cutoff,
        limit);
  }

  public CountMin countDeadLetterRequeueEligible(Instant cutoff) {
    return jdbc.queryForObject(
        """
        select count(*) as c, min(created_at) as m
        from notification_dead_letter_requeue_requests
        where created_at < ?
        """,
        COUNT_MIN,
        cutoff);
  }

  public int deleteDeadLetterRequeueBatch(Instant cutoff, int limit) {
    return jdbc.update(
        """
        delete from notification_dead_letter_requeue_requests
        where id in (
          select id from notification_dead_letter_requeue_requests
          where created_at < ?
          order by created_at asc
          limit ?
        )
        """,
        cutoff,
        limit);
  }

  public CountMin countDigestTerminalEligible(Instant cutoffSent, Instant cutoffCancelled) {
    return jdbc.queryForObject(
        """
        select count(*) as c,
               min(
                 case
                   when status = 'SENT' then coalesce(sent_at, created_at)
                   else created_at
                 end
               ) as m
        from notification_digest_items
        where status in ('SENT', 'CANCELLED')
          and (
            (status = 'SENT' and coalesce(sent_at, created_at) < ?)
            or (status = 'CANCELLED' and created_at < ?)
          )
        """,
        COUNT_MIN,
        cutoffSent,
        cutoffCancelled);
  }

  public int deleteDigestTerminalBatch(Instant cutoffSent, Instant cutoffCancelled, int limit) {
    return jdbc.update(
        """
        delete from notification_digest_items
        where id in (
          select id from notification_digest_items
          where status in ('SENT', 'CANCELLED')
            and (
              (status = 'SENT' and coalesce(sent_at, created_at) < ?)
              or (status = 'CANCELLED' and created_at < ?)
            )
          order by created_at asc
          limit ?
        )
        """,
        cutoffSent,
        cutoffCancelled,
        limit);
  }

  public CountMin countEmailTerminalEligible(Instant cutoff) {
    return jdbc.queryForObject(
        """
        select count(*) as c, min(updated_at) as m
        from email_notifications
        where status in ('SENT', 'FAILED', 'CANCELLED', 'SKIPPED')
          and updated_at < ?
        """,
        COUNT_MIN,
        cutoff);
  }

  public int deleteEmailTerminalBatch(Instant cutoff, int limit) {
    return jdbc.update(
        """
        delete from email_notifications
        where id in (
          select id from email_notifications
          where status in ('SENT', 'FAILED', 'CANCELLED', 'SKIPPED')
            and updated_at < ?
          order by updated_at asc
          limit ?
        )
        """,
        cutoff,
        limit);
  }

  private static Instant readInstant(ResultSet rs, String column) throws SQLException {
    var ts = rs.getTimestamp(column);
    return ts == null ? null : ts.toInstant();
  }

  public static Optional<Instant> oldestOrEmpty(CountMin cm) {
    if (cm == null || cm.count() == 0 || cm.oldestEligibleAt() == null) {
      return Optional.empty();
    }
    return Optional.of(cm.oldestEligibleAt());
  }
}
