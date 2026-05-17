package com.notebook.lumen.search.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SearchRetentionDataSourceHealthIndicatorTest {

  @Test
  void disabled_returnsDisabled() {
    var props = new SearchRetentionDataSourceProperties(false, "", "", null);
    var snapshot = SearchRetentionDataSourceDiagnostics.evaluate(props, null);
    assertThat(snapshot.lastCheckStatus()).isEqualTo("DISABLED");
  }

  @Test
  void enabledIncomplete_returnsNotConfigured() {
    var props = new SearchRetentionDataSourceProperties(true, "", "u", "p");
    var snapshot = SearchRetentionDataSourceDiagnostics.evaluate(props, null);
    assertThat(snapshot.lastCheckStatus()).isEqualTo("NOT_CONFIGURED");
    assertThat(snapshot.warningCodes())
        .contains(SearchRetentionDataSourceDiagnostics.WARNING_NOT_CONFIGURED);
  }

  @Test
  void connectionFailure_returnsSymbolicDown() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("password=leak"));
    var props = new SearchRetentionDataSourceProperties(true, "jdbc:x", "u", "p");
    var snapshot = SearchRetentionDataSourceDiagnostics.evaluate(props, dataSource);
    assertThat(snapshot.lastCheckStatus()).isEqualTo("DOWN");
    assertThat(String.valueOf(SearchRetentionDataSourceDiagnostics.toHealth(snapshot).getDetails()))
        .doesNotContain("password");
  }
}
