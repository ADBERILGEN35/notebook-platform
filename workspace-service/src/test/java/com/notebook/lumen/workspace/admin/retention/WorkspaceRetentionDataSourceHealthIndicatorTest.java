package com.notebook.lumen.workspace.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class WorkspaceRetentionDataSourceHealthIndicatorTest {

  @Test
  void disabled_returnsDisabled() {
    var props = new WorkspaceRetentionDataSourceProperties(false, "", "", null);
    var snapshot = WorkspaceRetentionDataSourceDiagnostics.evaluate(props, null);
    assertThat(snapshot.lastCheckStatus()).isEqualTo("DISABLED");
    assertThat(snapshot.fallbackToPrimary()).isTrue();
  }

  @Test
  void connectionFailure_returnsSymbolicDown() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("host=leak"));
    var props = new WorkspaceRetentionDataSourceProperties(true, "jdbc:x", "u", "p");
    var snapshot = WorkspaceRetentionDataSourceDiagnostics.evaluate(props, dataSource);
    assertThat(snapshot.lastCheckStatus()).isEqualTo("DOWN");
    assertThat(
            String.valueOf(WorkspaceRetentionDataSourceDiagnostics.toHealth(snapshot).getDetails()))
        .doesNotContain("leak");
  }
}
