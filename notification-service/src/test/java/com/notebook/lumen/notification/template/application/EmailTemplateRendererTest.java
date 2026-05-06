package com.notebook.lumen.notification.template.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EmailTemplateRendererTest {
  private final EmailTemplateRenderer renderer = new EmailTemplateRenderer();

  @Test
  void rendersWorkspaceInvitationAndEscapesHtmlVariables() {
    var rendered =
        renderer.render(
            "workspace-invitation",
            Map.of(
                "workspaceName",
                "<workspace>",
                "inviterEmail",
                "owner@example.com",
                "role",
                "ADMIN",
                "acceptUrl",
                "https://example.test/accept?token=secret"));

    assertThat(rendered.bodyText()).contains("<workspace>");
    assertThat(rendered.bodyHtml()).contains("&lt;workspace&gt;");
  }

  @Test
  void rejectsUnknownTemplate() {
    assertThatThrownBy(() -> renderer.render("missing", Map.of()))
        .isInstanceOf(NotificationException.class)
        .hasMessage("Email template not found");
  }
}
