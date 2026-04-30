package com.notebook.lumen.content.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties = "app.rls.enabled=true")
class TenantDatabaseSessionIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("notebook_platform")
          .withUsername("notebook")
          .withPassword("notebook");

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private TenantDatabaseSession tenantDatabaseSession;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private JdbcTemplate ownerJdbcTemplate;

  @Test
  void applyWorkspace_setsCurrentWorkspaceOnlyForCurrentTransaction() {
    UUID workspaceId = UUID.randomUUID();

    String inside =
        transactionTemplate.execute(
            status -> {
              tenantDatabaseSession.applyWorkspace(workspaceId);
              return currentWorkspaceSetting();
            });
    String nextTransaction = transactionTemplate.execute(status -> currentWorkspaceSetting());

    assertThat(inside).isEqualTo(workspaceId.toString());
    assertThat(nextTransaction).isNotEqualTo(workspaceId.toString());
  }

  @Test
  void nonOwnerRuntimeRole_respectsRlsAndForceRlsConstrainsOwner() {
    UUID workspaceA = UUID.randomUUID();
    UUID workspaceB = UUID.randomUUID();
    insertNote(workspaceA);
    insertNote(workspaceB);
    ownerJdbcTemplate.execute("create role content_runtime_test login password 'runtime'");
    ownerJdbcTemplate.execute("grant usage on schema public to content_runtime_test");
    ownerJdbcTemplate.execute(
        "grant select, insert, update, delete on notes, note_versions, note_links, comments, note_tags to content_runtime_test");
    JdbcTemplate runtimeJdbcTemplate = new JdbcTemplate(runtimeDataSource());

    assertThat(runtimeJdbcTemplate.queryForObject("select count(*) from notes", Integer.class))
        .isZero();
    assertThat(countNotesForWorkspace(runtimeJdbcTemplate, workspaceA)).isEqualTo(1);
    assertThat(countNotesForWorkspace(runtimeJdbcTemplate, workspaceB)).isEqualTo(1);

    ownerJdbcTemplate.execute("alter table notes force row level security");
    assertThat(isForceRlsEnabled("notes")).isTrue();
    ownerJdbcTemplate.execute("alter table notes no force row level security");
    assertThat(isForceRlsEnabled("notes")).isFalse();
  }

  private String currentWorkspaceSetting() {
    Object value =
        entityManager
            .createNativeQuery("select current_setting('app.current_workspace_id', true)")
            .getSingleResult();
    return value == null ? null : value.toString();
  }

  private void insertNote(UUID workspaceId) {
    ownerJdbcTemplate.update(
        """
        insert into notes (
          id, workspace_id, notebook_id, title, content_blocks, content_schema_version,
          created_by, created_at, updated_at
        ) values (?, ?, ?, ?, ?::jsonb, 1, ?, now(), now())
        """,
        UUID.randomUUID(),
        workspaceId,
        UUID.randomUUID(),
        "RLS note " + workspaceId,
        "[]",
        UUID.randomUUID());
  }

  private boolean isForceRlsEnabled(String tableName) {
    Boolean enabled =
        ownerJdbcTemplate.queryForObject(
            "select relforcerowsecurity from pg_class where relname = ?", Boolean.class, tableName);
    return Boolean.TRUE.equals(enabled);
  }

  private int countNotesForWorkspace(JdbcTemplate jdbcTemplate, UUID workspaceId) {
    DataSourceTransactionManager transactionManager =
        new DataSourceTransactionManager(jdbcTemplate.getDataSource());
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    Integer count =
        template.execute(
            status -> {
              jdbcTemplate.queryForObject(
                  "select set_config('app.current_workspace_id', ?, true)",
                  String.class,
                  workspaceId.toString());
              return jdbcTemplate.queryForObject("select count(*) from notes", Integer.class);
            });
    return count == null ? 0 : count;
  }

  private DataSource runtimeDataSource() {
    DriverManagerDataSource dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRES.getJdbcUrl());
    dataSource.setUsername("content_runtime_test");
    dataSource.setPassword("runtime");
    return dataSource;
  }
}
