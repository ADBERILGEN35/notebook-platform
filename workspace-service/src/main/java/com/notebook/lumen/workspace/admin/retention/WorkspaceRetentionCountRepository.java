package com.notebook.lumen.workspace.admin.retention;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkspaceRetentionCountRepository {

  private final JdbcTemplate jdbcTemplate;

  public WorkspaceRetentionCountRepository(
      DataSource dataSource,
      @Autowired(required = false)
          @Qualifier(WorkspaceRetentionJdbcTemplateConfig.RETENTION_JDBC_TEMPLATE_BEAN)
          JdbcTemplate retentionJdbcTemplate) {
    this.jdbcTemplate =
        retentionJdbcTemplate != null ? retentionJdbcTemplate : new JdbcTemplate(dataSource);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countExpiredPendingInvitationsBefore(Instant cutoff, int cap) {
    int safeCap = cap <= 0 ? 100_000 : cap;
    String sql =
        """
        SELECT count(*) FROM (
          SELECT 1 FROM invitations
          WHERE expires_at < ?
            AND accepted_at IS NULL
            AND revoked_at IS NULL
          LIMIT ?
        ) AS bounded
        """;
    Long result = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(cutoff), safeCap + 1);
    long total = result == null ? 0 : result;
    boolean capped = total > safeCap;
    return new CountResult(capped ? safeCap : total, capped);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countAuditEventsBefore(Instant cutoff, int cap) {
    return cappedCount("workspace_audit_events", "created_at", cutoff, cap);
  }

  private CountResult cappedCount(String table, String column, Instant cutoff, int cap) {
    int safeCap = cap <= 0 ? 100_000 : cap;
    String sql =
        "SELECT count(*) FROM (SELECT 1 FROM "
            + table
            + " WHERE "
            + column
            + " < ? LIMIT ?) AS bounded";
    Long result = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(cutoff), safeCap + 1);
    long total = result == null ? 0 : result;
    boolean capped = total > safeCap;
    return new CountResult(capped ? safeCap : total, capped);
  }

  public record CountResult(long count, boolean capped) {}
}
