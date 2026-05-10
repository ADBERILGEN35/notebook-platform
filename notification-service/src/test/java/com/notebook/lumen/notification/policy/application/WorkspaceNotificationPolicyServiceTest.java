package com.notebook.lumen.notification.policy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.NotificationTestFanout;
import com.notebook.lumen.notification.audit.AuditService;
import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceNotificationPolicyPatchItem;
import com.notebook.lumen.notification.policy.api.WorkspaceNotificationPolicyDtos.WorkspaceNotificationPolicyPatchRequest;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicyMode;
import com.notebook.lumen.notification.policy.infrastructure.WorkspaceNotificationPolicyRepository;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import com.notebook.lumen.notification.workspace.WorkspaceMembershipClient;
import com.notebook.lumen.notification.workspace.WorkspaceMembershipClient.WorkspaceMembershipPayload;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class WorkspaceNotificationPolicyServiceTest {

  @Mock WorkspaceNotificationPolicyRepository policyRepository;
  @Mock WorkspaceMembershipClient workspaceMembershipClient;
  @Mock AuditService auditService;

  private WorkspaceNotificationPolicyService service;
  private final UUID actor = UUID.randomUUID();
  private final UUID workspaceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    var outbound =
        new NotificationProperties.OutboundServiceJwt(
            "k", "key", "", "iss", "sub", "svc", 60, "aud");
    var workspace =
        new NotificationProperties.WorkspaceClient(
            true, true, true, "http://localhost:8082", 3000, outbound);
    NotificationProperties properties =
        new NotificationProperties(
            "",
            new NotificationProperties.Email(
                "noop",
                "no-reply@example.com",
                "",
                true,
                5,
                60,
                3600,
                5000,
                25,
                300,
                new NotificationProperties.Smtp("localhost", 587, "", "", true),
                new NotificationProperties.GenericHttp("", "", "Authorization", 1000, 3000),
                new NotificationProperties.Webhooks(
                    false,
                    "generic-http",
                    "",
                    "X-Email-Signature",
                    "X-Email-Timestamp",
                    300,
                    false,
                    false)),
            new NotificationProperties.Internal(null, null, null),
            new NotificationProperties.InApp(true),
            new NotificationProperties.Preferences(true),
            new NotificationProperties.Digest(
                true, true, 60, 100, 50, "09:00", java.time.DayOfWeek.MONDAY, "09:00"),
            NotificationTestFanout.disabled(),
            workspace);
    service =
        new WorkspaceNotificationPolicyService(
            policyRepository, workspaceMembershipClient, properties, auditService);
    when(workspaceMembershipClient.requireWorkspaceOwnerOrAdmin(any(), any()))
        .thenReturn(new WorkspaceMembershipPayload(workspaceId, actor, true, "OWNER"));
    when(policyRepository.findByWorkspaceId(workspaceId)).thenReturn(List.of());
  }

  @Test
  void patch_rejectsForceModeWithoutReasonWhenRequired() {
    var request =
        new WorkspaceNotificationPolicyPatchRequest(
            List.of(
                new WorkspaceNotificationPolicyPatchItem(
                    UserNotificationType.COMMENT_ADDED,
                    NotificationChannel.IN_APP,
                    WorkspaceNotificationPolicyMode.FORCE_ENABLED,
                    null)));

    assertThatThrownBy(() -> service.patch(actor, workspaceId, request))
        .isInstanceOf(NotificationException.class)
        .satisfies(
            ex ->
                assertThat(((NotificationException) ex).getErrorCode())
                    .isEqualTo("WORKSPACE_NOTIFICATION_POLICY_REASON_REQUIRED"));
  }

  @Test
  void patch_recordsAuditWhenAdminDenied() {
    var outbound =
        new NotificationProperties.OutboundServiceJwt(
            "k", "key", "", "iss", "sub", "svc", 60, "aud");
    var workspace =
        new NotificationProperties.WorkspaceClient(
            true, true, false, "http://localhost:8082", 3000, outbound);
    NotificationProperties properties =
        new NotificationProperties(
            "",
            new NotificationProperties.Email(
                "noop",
                "no-reply@example.com",
                "",
                true,
                5,
                60,
                3600,
                5000,
                25,
                300,
                new NotificationProperties.Smtp("localhost", 587, "", "", true),
                new NotificationProperties.GenericHttp("", "", "Authorization", 1000, 3000),
                new NotificationProperties.Webhooks(
                    false,
                    "generic-http",
                    "",
                    "X-Email-Signature",
                    "X-Email-Timestamp",
                    300,
                    false,
                    false)),
            new NotificationProperties.Internal(null, null, null),
            new NotificationProperties.InApp(true),
            new NotificationProperties.Preferences(true),
            new NotificationProperties.Digest(
                true, true, 60, 100, 50, "09:00", java.time.DayOfWeek.MONDAY, "09:00"),
            NotificationTestFanout.disabled(),
            workspace);
    var svc =
        new WorkspaceNotificationPolicyService(
            policyRepository, workspaceMembershipClient, properties, auditService);
    when(workspaceMembershipClient.requireWorkspaceOwnerOrAdmin(actor, workspaceId))
        .thenThrow(
            new NotificationException(
                HttpStatus.FORBIDDEN,
                "WORKSPACE_NOTIFICATION_POLICY_ACCESS_DENIED",
                "denied"));
    var request =
        new WorkspaceNotificationPolicyPatchRequest(
            List.of(
                new WorkspaceNotificationPolicyPatchItem(
                    UserNotificationType.COMMENT_ADDED,
                    NotificationChannel.IN_APP,
                    WorkspaceNotificationPolicyMode.USER_CONTROLLED,
                    null)));

    assertThatThrownBy(() -> svc.patch(actor, workspaceId, request))
        .isInstanceOf(NotificationException.class);
    verify(auditService)
        .record(
            eq("WORKSPACE_NOTIFICATION_POLICY_UPDATE_DENIED"),
            eq("WORKSPACE_NOTIFICATION_POLICY"),
            eq(workspaceId),
            any());
  }
}
