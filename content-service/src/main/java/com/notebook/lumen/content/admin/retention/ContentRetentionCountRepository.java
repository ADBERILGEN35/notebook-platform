package com.notebook.lumen.content.admin.retention;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregate-only retention count queries. Each query is cutoff-bounded and uses LIMIT to cap scan
 * cost; callers receive {@link CountResult} indicating whether the cap was reached. No note/comment
 * body, title, or workspace identifier is read.
 *
 * <p>RLS note: count queries are admin-scope cross-workspace. The runtime role must have BYPASSRLS
 * or queries must run with {@code row_security=off}. Tests assume Testcontainers default permissive
 * role; production requires DBA setup.
 */
@Repository
public class ContentRetentionCountRepository {

  private final JdbcTemplate jdbcTemplate;

  public ContentRetentionCountRepository(
      DataSource dataSource,
      @Autowired(required = false)
          @Qualifier(ContentRetentionJdbcTemplateConfig.RETENTION_JDBC_TEMPLATE_BEAN)
          JdbcTemplate retentionJdbcTemplate) {
    this.jdbcTemplate =
        retentionJdbcTemplate != null ? retentionJdbcTemplate : new JdbcTemplate(dataSource);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countNoteVersionsBefore(Instant cutoff, int cap) {
    return cappedCount("note_versions", "created_at", cutoff, cap);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countCommentsBefore(Instant cutoff, int cap) {
    return cappedCount("comments", "created_at", cutoff, cap);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countSearchDocumentsBefore(Instant cutoff, int cap) {
    return cappedCount("search_index_outbox", "created_at", cutoff, cap);
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
