package com.notebook.lumen.notification.admin.platformretention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class NotificationPlatformRetentionDataSourceHealthIndicatorTest {

  @Test
  void disabled_returnsDisabled() {
    var props = new NotificationPlatformRetentionDataSourceProperties(false, "", "", null);
    var snapshot = NotificationPlatformRetentionDataSourceDiagnostics.evaluate(props, null);
    assertThat(snapshot.lastCheckStatus()).isEqualTo("DISABLED");
    assertThat(
            String.valueOf(
                NotificationPlatformRetentionDataSourceDiagnostics.toHealth(snapshot).getDetails()))
        .doesNotContain("jdbc:");
  }

  @Test
  void connectionFailure_returnsDownWithoutRawSql() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("secret jdbc failure"));
    var props = new NotificationPlatformRetentionDataSourceProperties(true, "jdbc:x", "u", "p");
    var snapshot = NotificationPlatformRetentionDataSourceDiagnostics.evaluate(props, dataSource);
    assertThat(snapshot.lastCheckStatus()).isEqualTo("DOWN");
    assertThat(snapshot.warningCodes())
        .contains(NotificationPlatformRetentionDataSourceDiagnostics.WARNING_CONNECTION_FAILED);
    assertThat(
            String.valueOf(
                NotificationPlatformRetentionDataSourceDiagnostics.toHealth(snapshot).getDetails()))
        .doesNotContain("secret");
  }

  @Test
  void connectionOk_returnsUp() throws SQLException {
    Connection connection = mock(Connection.class);
    when(connection.isValid(2)).thenReturn(true);
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenReturn(connection);

    var props = new NotificationPlatformRetentionDataSourceProperties(true, "jdbc:x", "u", "p");
    var snapshot = NotificationPlatformRetentionDataSourceDiagnostics.evaluate(props, dataSource);
    assertThat(snapshot.lastCheckStatus()).isEqualTo("UP");
  }
}
