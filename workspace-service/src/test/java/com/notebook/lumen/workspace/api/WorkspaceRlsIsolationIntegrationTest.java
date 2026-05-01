package com.notebook.lumen.workspace.api;

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
class WorkspaceRlsIsolationIntegrationTest {
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

  @Autowired private JdbcTemplate ownerJdbcTemplate;

  @Test
  void nonOwnerRuntimeRole_enforcesTenantIsolationForceRlsAndRollback() {
    UUID workspaceA = UUID.randomUUID();
    UUID workspaceB = UUID.randomUUID();
    UUID notebookA = UUID.randomUUID();
    UUID notebookB = UUID.randomUUID();
    insertWorkspace(workspaceA, "a");
    insertWorkspace(workspaceB, "b");
    insertNotebook(notebookA, workspaceA, "A notebook");
    insertNotebook(notebookB, workspaceB, "B notebook");
    createRuntimeRole();
    JdbcTemplate runtime = new JdbcTemplate(runtimeDataSource());

    assertRuntimeRoleIsNotOwnerOrBypassRls();
    assertThatThrownBy(() -> runtime.execute("create table rls_ddl_probe(id int)"))
        .hasStackTraceContaining("permission denied");

    assertThat(runtime.queryForObject("select count(*) from notebooks", Integer.class)).isZero();
    assertThat(countNotebooks(runtime, workspaceA)).isEqualTo(1);
    assertThat(countNotebooks(runtime, workspaceB)).isEqualTo(1);
    assertThat(countNotebookById(runtime, workspaceA, notebookB)).isZero();
    assertThat(countNotebookById(runtime, workspaceB, notebookA)).isZero();

    runSql("scripts/db/enable-force-rls-workspace.sql");
    assertForceRls(
        true,
        "workspace_members",
        "notebooks",
        "notebook_members",
        "tags",
        "notebook_tags",
        "invitations");
    assertThat(countNotebookById(runtime, workspaceA, notebookB)).isZero();

    runSql("scripts/db/disable-force-rls-workspace.sql");
    assertForceRls(
        false,
        "workspace_members",
        "notebooks",
        "notebook_members",
        "tags",
        "notebook_tags",
        "invitations");
  }

  private void createRuntimeRole() {
    ownerJdbcTemplate.execute("create role workspace_runtime login password 'runtime'");
    ownerJdbcTemplate.execute("grant usage on schema public to workspace_runtime");
    ownerJdbcTemplate.execute(
        """
        grant select, insert, update, delete on
          workspaces, workspace_members, notebooks, notebook_members,
          tags, notebook_tags, invitations
        to workspace_runtime
        """);
  }

  private void assertRuntimeRoleIsNotOwnerOrBypassRls() {
    Boolean bypassesRls =
        ownerJdbcTemplate.queryForObject(
            "select rolbypassrls from pg_roles where rolname = 'workspace_runtime'", Boolean.class);
    Boolean isOwner =
        ownerJdbcTemplate.queryForObject(
            """
            select exists (
              select 1 from pg_class c
              join pg_namespace n on n.oid = c.relnamespace
              where n.nspname = 'public'
                and c.relname in ('workspace_members', 'notebooks', 'notebook_members', 'tags', 'notebook_tags', 'invitations')
                and c.relowner::regrole::text = 'workspace_runtime'
            )
            """,
            Boolean.class);
    assertThat(bypassesRls).isFalse();
    assertThat(isOwner).isFalse();
  }

  private void insertWorkspace(UUID workspaceId, String suffix) {
    ownerJdbcTemplate.update(
        """
        insert into workspaces (id, slug, name, type, owner_id, created_at, updated_at)
        values (?, ?, ?, 'TEAM', ?, now(), now())
        """,
        workspaceId,
        "rls-" + suffix + "-" + workspaceId,
        "RLS " + suffix,
        UUID.randomUUID());
  }

  private void insertNotebook(UUID notebookId, UUID workspaceId, String name) {
    ownerJdbcTemplate.update(
        """
        insert into notebooks (id, workspace_id, name, created_by, created_at, updated_at)
        values (?, ?, ?, ?, now(), now())
        """,
        notebookId,
        workspaceId,
        name,
        UUID.randomUUID());
  }

  private int countNotebooks(JdbcTemplate jdbcTemplate, UUID workspaceId) {
    Integer count =
        inRuntimeTransaction(
            jdbcTemplate,
            workspaceId,
            () -> jdbcTemplate.queryForObject("select count(*) from notebooks", Integer.class));
    return count == null ? 0 : count;
  }

  private int countNotebookById(JdbcTemplate jdbcTemplate, UUID workspaceId, UUID notebookId) {
    Integer count =
        inRuntimeTransaction(
            jdbcTemplate,
            workspaceId,
            () ->
                jdbcTemplate.queryForObject(
                    "select count(*) from notebooks where id = ?", Integer.class, notebookId));
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
    dataSource.setUsername("workspace_runtime");
    dataSource.setPassword("runtime");
    return dataSource;
  }

  private interface Callback<T> {
    T run();
  }
}
