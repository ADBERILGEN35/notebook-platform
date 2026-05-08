package com.notebook.lumen.notification.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.notification.NotificationTestFanout;
import com.notebook.lumen.notification.NotificationTestWorkspace;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.config.NotificationSseProperties;
import org.junit.jupiter.api.Test;

class NotificationAdminStatusServiceTest {

  @Test
  void jsonOmitsSecrets() throws Exception {
    var email =
        new NotificationProperties.Email(
            "smtp",
            "from@example.com",
            "",
            true,
            5,
            60,
            3600,
            5000,
            25,
            300,
            new NotificationProperties.Smtp("smtp.example.com", 587, "u", "secret-pass", true),
            new NotificationProperties.GenericHttp(
                "https://api.example", "api-key-secret", "Authorization", 1000, 3000),
            new NotificationProperties.Webhooks(
                true,
                "generic-http",
                "whsec",
                "X-Sig",
                "X-Ts",
                300,
                false,
                false));
    var props =
        new NotificationProperties(
            "",
            email,
            new NotificationProperties.Internal(null, null, null),
            new NotificationProperties.InApp(true),
            new NotificationProperties.Preferences(true),
            new NotificationProperties.Digest(true, true, 60, 100, 50, "09:00", java.time.DayOfWeek.MONDAY, "09:00"),
        NotificationTestFanout.disabled(),
        NotificationTestWorkspace.disabled());
    var sse = new NotificationSseProperties();
    sse.setEnabled(true);
    sse.getDistributed().setEnabled(true);
    sse.getDistributed().setChannel("notification:sse:events");

    var service = new NotificationAdminStatusService(props, sse);
    NotificationAdminStatusResponse body = service.build();
    String json = new ObjectMapper().writeValueAsString(body);
    assertThat(json).doesNotContain("secret-pass", "api-key-secret", "whsec");
    assertThat(body.sse().redisChannelConfigured()).isTrue();
  }
}
