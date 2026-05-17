package com.notebook.lumen.content.admin.retention;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(ContentRetentionDataSourceProperties.class)
public class ContentRetentionJdbcTemplateConfig {

  public static final String RETENTION_DATA_SOURCE_BEAN = "contentRetentionDataSource";
  public static final String RETENTION_JDBC_TEMPLATE_BEAN = "contentRetentionJdbcTemplate";

  @Bean(name = RETENTION_DATA_SOURCE_BEAN)
  @ConditionalOnProperty(
      prefix = "content.retention.datasource",
      name = "enabled",
      havingValue = "true")
  DataSource contentRetentionDataSource(ContentRetentionDataSourceProperties properties) {
    properties.assertCompleteIfEnabled();
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setPoolName("content-retention");
    dataSource.setJdbcUrl(properties.url());
    dataSource.setUsername(properties.username());
    dataSource.setPassword(properties.password());
    dataSource.setMaximumPoolSize(2);
    dataSource.setMinimumIdle(0);
    dataSource.setInitializationFailTimeout(-1);
    return dataSource;
  }

  @Bean(name = RETENTION_JDBC_TEMPLATE_BEAN)
  @ConditionalOnProperty(
      prefix = "content.retention.datasource",
      name = "enabled",
      havingValue = "true")
  JdbcTemplate contentRetentionJdbcTemplate(
      @Qualifier(RETENTION_DATA_SOURCE_BEAN) DataSource contentRetentionDataSource) {
    return new JdbcTemplate(contentRetentionDataSource);
  }
}
