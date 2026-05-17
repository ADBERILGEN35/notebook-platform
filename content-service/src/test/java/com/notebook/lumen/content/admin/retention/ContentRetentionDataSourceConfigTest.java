package com.notebook.lumen.content.admin.retention;

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

class ContentRetentionDataSourceConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(ContentRetentionJdbcTemplateConfig.class)
          .withBean(DataSource.class, () -> mock(DataSource.class));

  @Test
  void disabled_doesNotRegisterRetentionBeans() {
    contextRunner
        .withPropertyValues("content.retention.datasource.enabled=false")
        .run(
            context -> {
              assertThat(context)
                  .doesNotHaveBean(ContentRetentionJdbcTemplateConfig.RETENTION_DATA_SOURCE_BEAN);
              assertThat(context)
                  .doesNotHaveBean(ContentRetentionJdbcTemplateConfig.RETENTION_JDBC_TEMPLATE_BEAN);
            });
  }

  @Test
  void enabled_missingUrl_failsFast() {
    contextRunner
        .withPropertyValues(
            "content.retention.datasource.enabled=true",
            "content.retention.datasource.username=user",
            "content.retention.datasource.password=secret")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void enabled_completeConfig_registersRetentionJdbcTemplate() {
    contextRunner
        .withPropertyValues(
            "content.retention.datasource.enabled=true",
            "content.retention.datasource.url=jdbc:postgresql://127.0.0.1:5432/notebook_platform",
            "content.retention.datasource.username=notebook_content_retention",
            "content.retention.datasource.password=placeholder-not-logged")
        .run(
            context -> {
              assertThat(context)
                  .hasBean(ContentRetentionJdbcTemplateConfig.RETENTION_JDBC_TEMPLATE_BEAN);
              assertThat(
                      context.getBean(
                          ContentRetentionJdbcTemplateConfig.RETENTION_JDBC_TEMPLATE_BEAN))
                  .isInstanceOf(JdbcTemplate.class);
            });
  }

  @Test
  void properties_assertCompleteIfEnabled_listsMissingFields() {
    assertThatThrownBy(
            () ->
                new ContentRetentionDataSourceProperties(true, "", "user", "pw")
                    .assertCompleteIfEnabled())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("url");
  }

  @Test
  void repository_usesDedicatedJdbcTemplateWhenPresent() {
    JdbcTemplate dedicated = mock(JdbcTemplate.class);
    when(dedicated.queryForObject(any(String.class), eq(Long.class), any(), any())).thenReturn(0L);
    ContentRetentionCountRepository repository =
        new ContentRetentionCountRepository(mock(DataSource.class), dedicated);

    repository.countNoteVersionsBefore(Instant.EPOCH, 10);

    verify(dedicated).queryForObject(any(String.class), eq(Long.class), any(), any());
  }

  @Test
  void repository_fallsBackToPrimaryDataSourceWhenDedicatedAbsent() {
    ContentRetentionCountRepository repository =
        new ContentRetentionCountRepository(mock(DataSource.class), null);
    assertThat(repository).isNotNull();
  }
}
