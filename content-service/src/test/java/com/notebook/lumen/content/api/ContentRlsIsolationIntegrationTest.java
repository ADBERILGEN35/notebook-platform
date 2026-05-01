package com.notebook.lumen.content.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ActiveProfiles("rls-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ContentRlsIsolationIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("notebook_platform")
          .withUsername("notebook")
          .withPassword("notebook");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private JdbcTemplate ownerJdbcTemplate;

  @Test
  void nonOwnerRuntimeRole_enforcesTenantIsolationAcrossContentTablesAndForceRollback() {
    UUID workspaceA = UUID.randomUUID();
    UUID workspaceB = UUID.randomUUID();
    UUID noteA = UUID.randomUUID();
    UUID noteB = UUID.randomUUID();
    UUID commentA = UUID.randomUUID();
    UUID commentB = UUID.randomUUID();
    UUID tagA = UUID.randomUUID();
    UUID tagB = UUID.randomUUID();
    insertContentGraph(workspaceA, noteA, commentA, tagA, "Alpha searchable");
    insertContentGraph(workspaceB, noteB, commentB, tagB, "Beta searchable");
    insertLink(workspaceA, noteA, noteA);
    insertLink(workspaceB, noteB, noteB);
    createRuntimeRole();
    JdbcTemplate runtime = new JdbcTemplate(runtimeDataSource());

    assertRuntimeRoleIsNotOwnerOrBypassRls();
    assertThatThrownBy(() -> runtime.execute("create table content_rls_ddl_probe(id int)"))
        .hasStackTraceContaining("permission denied");

    assertThat(runtime.queryForObject("select count(*) from notes", Integer.class)).isZero();
    assertThat(count(runtime, workspaceA, "notes")).isEqualTo(1);
    assertThat(count(runtime, workspaceB, "notes")).isEqualTo(1);
    assertThat(countById(runtime, workspaceA, "notes", "id", noteB)).isZero();
    assertThat(countById(runtime, workspaceB, "notes", "id", noteA)).isZero();
    assertThat(countById(runtime, workspaceA, "note_versions", "note_id", noteB)).isZero();
    assertThat(countById(runtime, workspaceA, "comments", "id", commentB)).isZero();
    assertThat(countById(runtime, workspaceA, "note_tags", "tag_id", tagB)).isZero();
    assertThat(searchCount(runtime, workspaceA, "Beta")).isZero();
    assertThat(searchCount(runtime, workspaceB, "Alpha")).isZero();
    assertThat(count(runtime, workspaceA, "note_links")).isEqualTo(1);

    runSql("scripts/db/enable-force-rls-content.sql");
    assertForceRls(true, "notes", "note_versions", "note_links", "comments", "note_tags");
    assertThat(countById(runtime, workspaceA, "notes", "id", noteB)).isZero();

    runSql("scripts/db/disable-force-rls-content.sql");
    assertForceRls(false, "notes", "note_versions", "note_links", "comments", "note_tags");
  }

  private void createRuntimeRole() {
    ownerJdbcTemplate.execute("create role content_runtime login password 'runtime'");
    ownerJdbcTemplate.execute("grant usage on schema public to content_runtime");
    ownerJdbcTemplate.execute(
        """
        grant select, insert, update, delete on
          notes, note_versions, note_links, comments, note_tags
        to content_runtime
        """);
  }

  private void assertRuntimeRoleIsNotOwnerOrBypassRls() {
    Boolean bypassesRls =
        ownerJdbcTemplate.queryForObject(
            "select rolbypassrls from pg_roles where rolname = 'content_runtime'", Boolean.class);
    Boolean isOwner =
        ownerJdbcTemplate.queryForObject(
            """
            select exists (
              select 1 from pg_class c
              join pg_namespace n on n.oid = c.relnamespace
              where n.nspname = 'public'
                and c.relname in ('notes', 'note_versions', 'note_links', 'comments', 'note_tags')
                and c.relowner::regrole::text = 'content_runtime'
            )
            """,
            Boolean.class);
    assertThat(bypassesRls).isFalse();
    assertThat(isOwner).isFalse();
  }

  private void insertContentGraph(
      UUID workspaceId, UUID noteId, UUID commentId, UUID tagId, String title) {
    ownerJdbcTemplate.update(
        """
        insert into notes (
          id, workspace_id, notebook_id, title, content_blocks, content_schema_version,
          created_by, created_at, updated_at
        ) values (?, ?, ?, ?, ?::jsonb, 1, ?, now(), now())
        """,
        noteId,
        workspaceId,
        UUID.randomUUID(),
        title,
        "[]",
        UUID.randomUUID());
    ownerJdbcTemplate.update(
        """
        insert into note_versions (
          id, workspace_id, note_id, version_number, title, content_blocks,
          content_schema_version, created_by, created_at
        ) values (?, ?, ?, 1, ?, ?::jsonb, 1, ?, now())
        """,
        UUID.randomUUID(),
        workspaceId,
        noteId,
        title,
        "[]",
        UUID.randomUUID());
    ownerJdbcTemplate.update(
        """
        insert into comments (
          id, workspace_id, note_id, user_id, content, created_at, updated_at
        ) values (?, ?, ?, ?, 'comment', now(), now())
        """,
        commentId,
        workspaceId,
        noteId,
        UUID.randomUUID());
    ownerJdbcTemplate.update(
        "insert into note_tags (note_id, tag_id, workspace_id, created_at) values (?, ?, ?, now())",
        noteId,
        tagId,
        workspaceId);
  }

  private void insertLink(UUID workspaceId, UUID fromNoteId, UUID toNoteId) {
    ownerJdbcTemplate.update(
        """
        insert into note_links (from_note_id, to_note_id, workspace_id, created_at)
        values (?, ?, ?, now())
        """,
        fromNoteId,
        toNoteId,
        workspaceId);
  }

  private int count(JdbcTemplate jdbcTemplate, UUID workspaceId, String tableName) {
    Integer count =
        inRuntimeTransaction(
            jdbcTemplate,
            workspaceId,
            () -> jdbcTemplate.queryForObject("select count(*) from " + tableName, Integer.class));
    return count == null ? 0 : count;
  }

  private int countById(
      JdbcTemplate jdbcTemplate, UUID workspaceId, String tableName, String columnName, UUID id) {
    Integer count =
        inRuntimeTransaction(
            jdbcTemplate,
            workspaceId,
            () ->
                jdbcTemplate.queryForObject(
                    "select count(*) from " + tableName + " where " + columnName + " = ?",
                    Integer.class,
                    id));
    return count == null ? 0 : count;
  }

  private int searchCount(JdbcTemplate jdbcTemplate, UUID workspaceId, String query) {
    Integer count =
        inRuntimeTransaction(
            jdbcTemplate,
            workspaceId,
            () ->
                jdbcTemplate.queryForObject(
                    "select count(*) from notes where search_vector @@ plainto_tsquery('simple', ?)",
                    Integer.class,
                    query));
    return count == null ? 0 : count;
  }

  private <T> T inRuntimeTransaction(JdbcTemplate jdbcTemplate, UUID workspaceId, Callback<T> cb) {
    TransactionTemplate template =
        new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    return template.execute(
        status -> {
          jdbcTemplate.queryForObject(
              "select set_config('app.current_workspace_id', ?, true)",
              String.class,
              workspaceId.toString());
          return cb.run();
        });
  }

  private void runSql(String path) {
    new ResourceDatabasePopulator(new FileSystemResource(resolveFromRepoRoot(path)))
        .execute(ownerJdbcTemplate.getDataSource());
  }

  private Path resolveFromRepoRoot(String path) {
    Path direct = Path.of(path);
    if (direct.toFile().exists()) {
      return direct;
    }
    return Path.of("..").resolve(path);
  }

  private void assertForceRls(boolean expected, String... tableNames) {
    List<Boolean> values =
        ownerJdbcTemplate.query(
            "select relforcerowsecurity from pg_class where relname = any (?) order by relname",
            (rs, rowNum) -> rs.getBoolean(1),
            (Object) tableNames);
    assertThat(values).hasSize(tableNames.length).allMatch(value -> value == expected);
  }

  private DataSource runtimeDataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername("content_runtime");
    dataSource.setPassword("runtime");
    return dataSource;
  }

  private interface Callback<T> {
    T run();
  }
}
