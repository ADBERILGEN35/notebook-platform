package com.notebook.lumen.search.admin.retention;

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
public class SearchRetentionCountRepository {

  private final JdbcTemplate jdbcTemplate;

  public SearchRetentionCountRepository(
      DataSource dataSource,
      @Autowired(required = false)
          @Qualifier(SearchRetentionJdbcTemplateConfig.RETENTION_JDBC_TEMPLATE_BEAN)
          JdbcTemplate retentionJdbcTemplate) {
    this.jdbcTemplate =
        retentionJdbcTemplate != null ? retentionJdbcTemplate : new JdbcTemplate(dataSource);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countArchivedDocumentsBefore(Instant cutoff, int cap) {
    return cappedCount(
        "search_documents", "archived_at IS NOT NULL AND archived_at < ?", cutoff, cap);
  }

  @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
  public CountResult countTerminalReindexJobsBefore(Instant cutoff, int cap) {
    int safeCap = cap <= 0 ? 100_000 : cap;
    String sql =
        """
        SELECT count(*) FROM (
          SELECT 1 FROM search_reindex_jobs
          WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')
            AND COALESCE(completed_at, failed_at, updated_at, created_at) < ?
          LIMIT ?
        ) AS bounded
        """;
    Long result = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(cutoff), safeCap + 1);
    long total = result == null ? 0 : result;
    boolean capped = total > safeCap;
    return new CountResult(capped ? safeCap : total, capped);
  }

  private CountResult cappedCount(String table, String predicate, Instant cutoff, int cap) {
    int safeCap = cap <= 0 ? 100_000 : cap;
    String sql =
        "SELECT count(*) FROM (SELECT 1 FROM "
            + table
            + " WHERE "
            + predicate
            + " LIMIT ?) AS bounded";
    Long result = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(cutoff), safeCap + 1);
    long total = result == null ? 0 : result;
    boolean capped = total > safeCap;
    return new CountResult(capped ? safeCap : total, capped);
  }

  public record CountResult(long count, boolean capped) {}
}
