package com.notebook.lumen.workspace.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkspaceRetentionDataSourceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(WorkspaceRetentionJdbcTemplateConfig.class)
          .withBean(DataSource.class, () -> mock(DataSource.class));

  @Test
  void disabled_doesNotRegisterRetentionBeans() {
    contextRunner
        .withPropertyValues("workspace.retention.datasource.enabled=false")
        .run(
            context -> {
              assertThat(context)
                  .doesNotHaveBean(WorkspaceRetentionJdbcTemplateConfig.RETENTION_DATA_SOURCE_BEAN);
              assertThat(context)
                  .doesNotHaveBean(
                      WorkspaceRetentionJdbcTemplateConfig.RETENTION_JDBC_TEMPLATE_BEAN);
            });
  }

  @Test
  void enabled_missingUrl_failsFast() {
    contextRunner
        .withPropertyValues(
            "workspace.retention.datasource.enabled=true",
            "workspace.retention.datasource.username=user",
            "workspace.retention.datasource.password=secret")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void enabled_completeConfig_registersRetentionJdbcTemplate() {
    contextRunner
        .withPropertyValues(
            "workspace.retention.datasource.enabled=true",
            "workspace.retention.datasource.url=jdbc:postgresql://127.0.0.1:5432/notebook_platform",
            "workspace.retention.datasource.username=notebook_workspace_retention",
            "workspace.retention.datasource.password=placeholder")
        .run(
            context -> {
              assertThat(context)
                  .hasBean(WorkspaceRetentionJdbcTemplateConfig.RETENTION_JDBC_TEMPLATE_BEAN);
            });
  }

  @Test
  void properties_assertCompleteIfEnabled_listsMissingFields() {
    assertThatThrownBy(
            () ->
                new WorkspaceRetentionDataSourceProperties(true, "", "user", "pw")
                    .assertCompleteIfEnabled())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("url");
  }

  @Test
  void repository_usesDedicatedJdbcTemplateWhenPresent() {
    JdbcTemplate dedicated = mock(JdbcTemplate.class);
    when(dedicated.queryForObject(any(String.class), eq(Long.class), any(), any())).thenReturn(0L);
    WorkspaceRetentionCountRepository repository =
        new WorkspaceRetentionCountRepository(mock(DataSource.class), dedicated);

    repository.countAuditEventsBefore(Instant.EPOCH, 10);

    verify(dedicated).queryForObject(any(String.class), eq(Long.class), any(), any());
  }
}
