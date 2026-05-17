package com.notebook.lumen.content.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;

class ContentRetentionDataSourceHealthIndicatorTest {

  @Test
  void disabled_returnsDisabledFallbackWithoutSecrets() {
    ContentRetentionDataSourceProperties props =
        new ContentRetentionDataSourceProperties(false, "", "", null);

    ContentRetentionDataSourceDiagnostics.Snapshot snapshot =
        ContentRetentionDataSourceDiagnostics.evaluate(props, null);

    assertThat(snapshot.lastCheckStatus()).isEqualTo("DISABLED");
    assertThat(snapshot.fallbackToPrimary()).isTrue();
    assertThat(snapshot.warningCodes())
        .containsExactly(ContentRetentionDataSourceDiagnostics.WARNING_DISABLED);

    Health health = ContentRetentionDataSourceDiagnostics.toHealth(snapshot);
    assertHealthHasNoSecrets(health);
    assertThat(health.getDetails()).containsEntry("lastCheckStatus", "DISABLED");
  }

  @Test
  void enabledIncompleteConfig_returnsNotConfigured() {
    ContentRetentionDataSourceProperties props =
        new ContentRetentionDataSourceProperties(true, "", "user", "pw");

    ContentRetentionDataSourceDiagnostics.Snapshot snapshot =
        ContentRetentionDataSourceDiagnostics.evaluate(props, null);

    assertThat(snapshot.lastCheckStatus()).isEqualTo("NOT_CONFIGURED");
    assertThat(snapshot.warningCodes())
        .contains(ContentRetentionDataSourceDiagnostics.WARNING_NOT_CONFIGURED);
    assertHealthHasNoSecrets(ContentRetentionDataSourceDiagnostics.toHealth(snapshot));
  }

  @Test
  void enabledCompleteNoPool_returnsFallbackPrimary() {
    ContentRetentionDataSourceProperties props =
        new ContentRetentionDataSourceProperties(true, "jdbc:postgresql://x", "u", "p");

    ContentRetentionDataSourceDiagnostics.Snapshot snapshot =
        ContentRetentionDataSourceDiagnostics.evaluate(props, null);

    assertThat(snapshot.lastCheckStatus()).isEqualTo("FALLBACK_PRIMARY");
    assertThat(snapshot.configComplete()).isTrue();
    assertThat(snapshot.fallbackToPrimary()).isTrue();
  }

  @Test
  void enabledConnectionOk_returnsUp() throws SQLException {
    DataSource dataSource = mockValidDataSource();
    ContentRetentionDataSourceProperties props =
        new ContentRetentionDataSourceProperties(true, "jdbc:internal", "role", "secret");

    ContentRetentionDataSourceDiagnostics.Snapshot snapshot =
        ContentRetentionDataSourceDiagnostics.evaluate(props, dataSource);

    assertThat(snapshot.lastCheckStatus()).isEqualTo("UP");
    assertThat(snapshot.usingDedicatedDatasource()).isTrue();
    assertThat(snapshot.fallbackToPrimary()).isFalse();
    assertThat(snapshot.warningCodes()).isEmpty();

    Health health = ContentRetentionDataSourceDiagnostics.toHealth(snapshot);
    assertHealthHasNoSecrets(health);
    assertThat(health.getStatus().getCode()).isEqualTo("UP"); // aggregate health UP
  }

  @Test
  void enabledConnectionFails_returnsDownWithSymbolicWarning() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection())
        .thenThrow(new SQLException("connection to jdbc:secret-host failed for user leak"));

    ContentRetentionDataSourceProperties props =
        new ContentRetentionDataSourceProperties(true, "jdbc:internal", "role", "secret");

    ContentRetentionDataSourceDiagnostics.Snapshot snapshot =
        ContentRetentionDataSourceDiagnostics.evaluate(props, dataSource);

    assertThat(snapshot.lastCheckStatus()).isEqualTo("DOWN");
    assertThat(snapshot.warningCodes())
        .containsExactly(ContentRetentionDataSourceDiagnostics.WARNING_CONNECTION_FAILED);

    Health health = ContentRetentionDataSourceDiagnostics.toHealth(snapshot);
    assertHealthHasNoSecrets(health);
    assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
  }

  @Test
  void indicator_disabled_delegatesToDiagnostics() {
    @SuppressWarnings("unchecked")
    ObjectProvider<DataSource> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);

    ContentRetentionDataSourceHealthIndicator indicator =
        new ContentRetentionDataSourceHealthIndicator(
            new ContentRetentionDataSourceProperties(false, "", "", null), provider);

    Health health = indicator.health();
    assertThat(health.getDetails()).containsEntry("lastCheckStatus", "DISABLED");
    assertHealthHasNoSecrets(health);
  }

  private static DataSource mockValidDataSource() throws SQLException {
    Connection connection = mock(Connection.class);
    when(connection.isValid(2)).thenReturn(true);
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenReturn(connection);
    return dataSource;
  }

  private static void assertHealthHasNoSecrets(Health health) {
    String serialized = String.valueOf(health.getDetails());
    assertThat(serialized)
        .doesNotContain("jdbc:")
        .doesNotContain("postgresql")
        .doesNotContain("password")
        .doesNotContain("secret-host")
        .doesNotContain("SQLException")
        .doesNotContain("leak");
    for (Object value : health.getDetails().values()) {
      if (value instanceof Map<?, ?> map) {
        assertThat(String.valueOf(map)).doesNotContain("jdbc:");
      }
    }
  }
}
