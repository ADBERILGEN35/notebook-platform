package com.notebook.lumen.notification.preference.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.notification.NotificationTestFanout;
import com.notebook.lumen.notification.NotificationTestWorkspace;
import com.notebook.lumen.notification.analytics.NotificationAnalyticsEventKind;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicy;
import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicyMode;
import com.notebook.lumen.notification.policy.infrastructure.WorkspaceNotificationPolicyRepository;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.preference.domain.UserWorkspaceNotificationPreference;
import com.notebook.lumen.notification.preference.infrastructure.UserWorkspaceNotificationPreferenceRepository;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationPreferenceResolverTest {
  private final NotificationPreferenceService global = mock(NotificationPreferenceService.class);
  private final UserWorkspaceNotificationPreferenceRepository workspaceRepo =
      mock(UserWorkspaceNotificationPreferenceRepository.class);
  private final WorkspaceNotificationPolicyRepository policyRepo =
      mock(WorkspaceNotificationPolicyRepository.class);

  private NotificationPreferenceResolver resolver(boolean workspacePrefsEnabled) {
    return resolver(workspacePrefsEnabled, false);
  }

  private NotificationPreferenceResolver resolver(boolean workspacePrefsEnabled, boolean policiesEnabled) {
    NotificationProperties.WorkspaceClient workspace =
        workspacePrefsEnabled
            ? new NotificationProperties.WorkspaceClient(
                true,
                policiesEnabled,
                false,
                "http://localhost:8082",
                3000,
                new NotificationProperties.OutboundServiceJwt(
                    "k",
                    "dummy-private-key",
                    "",
                    "notification-service",
                    "service:notification-service",
                    "notification-service",
                    60,
                    "workspace-service"))
            : NotificationTestWorkspace.disabled();
    NotificationProperties props =
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
    return new NotificationPreferenceResolver(global, workspaceRepo, policyRepo, props);
  }

  @Test
  void mandatorySecurityTypeAlwaysEnabled() {
    var resolver = resolver(true);
    assertThat(
            resolver.isChannelEnabled(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UserNotificationType.SECURITY_SESSIONS_REVOKED,
                NotificationChannel.EMAIL))
        .isTrue();
  }

  @Test
  void nullUserIdAlwaysEnabled() {
    var resolver = resolver(true);
    assertThat(
            resolver.isChannelEnabled(
                null,
                UUID.randomUUID(),
                UserNotificationType.COMMENT_ADDED,
                NotificationChannel.EMAIL))
        .isTrue();
  }

  @Test
  void workspaceOverrideUsedWhenFeatureOnAndRowExists() {
    UUID user = UUID.randomUUID();
    UUID ws = UUID.randomUUID();
    when(global.isEnabled(
            eq(user), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(true);
    when(workspaceRepo.findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
            eq(user), eq(ws), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(
            Optional.of(
                new UserWorkspaceNotificationPreference(
                    UUID.randomUUID(),
                    user,
                    ws,
                    UserNotificationType.COMMENT_ADDED,
                    NotificationChannel.EMAIL,
                    false,
                    Instant.now())));
    when(policyRepo.findByWorkspaceIdAndNotificationTypeAndChannel(
            eq(ws), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(Optional.empty());
    var resolver = resolver(true);
    assertThat(
            resolver.isChannelEnabled(
                user, ws, UserNotificationType.COMMENT_ADDED, NotificationChannel.EMAIL))
        .isFalse();
  }

  @Test
  void noOverrideFallsBackToGlobal() {
    UUID user = UUID.randomUUID();
    UUID ws = UUID.randomUUID();
    when(global.isEnabled(
            eq(user), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(false);
    when(workspaceRepo.findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
            any(), any(), any(), any()))
        .thenReturn(Optional.empty());
    when(policyRepo.findByWorkspaceIdAndNotificationTypeAndChannel(any(), any(), any()))
        .thenReturn(Optional.empty());
    var resolver = resolver(true);
    assertThat(
            resolver.isChannelEnabled(
                user, ws, UserNotificationType.COMMENT_ADDED, NotificationChannel.EMAIL))
        .isFalse();
  }

  @Test
  void featureDisabledUsesGlobalOnly() {
    UUID user = UUID.randomUUID();
    UUID ws = UUID.randomUUID();
    when(global.isEnabled(
            eq(user), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(true);
    when(workspaceRepo.findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
            eq(user), eq(ws), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(
            Optional.of(
                new UserWorkspaceNotificationPreference(
                    UUID.randomUUID(),
                    user,
                    ws,
                    UserNotificationType.COMMENT_ADDED,
                    NotificationChannel.EMAIL,
                    false,
                    Instant.now())));
    var resolver = resolver(false);
    assertThat(
            resolver.isChannelEnabled(
                user, ws, UserNotificationType.COMMENT_ADDED, NotificationChannel.EMAIL))
        .isTrue();
  }

  @Test
  void nullWorkspaceIdUsesGlobal() {
    UUID user = UUID.randomUUID();
    when(global.isEnabled(
            eq(user), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.IN_APP)))
        .thenReturn(true);
    var resolver = resolver(true);
    assertThat(
            resolver.isChannelEnabled(
                user, null, UserNotificationType.COMMENT_ADDED, NotificationChannel.IN_APP))
        .isTrue();
  }

  @Test
  void forceEnabledOverridesUserDisabledPreference() {
    UUID user = UUID.randomUUID();
    UUID ws = UUID.randomUUID();
    when(global.isEnabled(
            eq(user), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.IN_APP)))
        .thenReturn(true);
    when(workspaceRepo.findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
            eq(user), eq(ws), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.IN_APP)))
        .thenReturn(
            Optional.of(
                new UserWorkspaceNotificationPreference(
                    UUID.randomUUID(),
                    user,
                    ws,
                    UserNotificationType.COMMENT_ADDED,
                    NotificationChannel.IN_APP,
                    false,
                    Instant.now())));
    when(policyRepo.findByWorkspaceIdAndNotificationTypeAndChannel(
            eq(ws), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.IN_APP)))
        .thenReturn(
            Optional.of(
                new WorkspaceNotificationPolicy(
                    UUID.randomUUID(),
                    ws,
                    UserNotificationType.COMMENT_ADDED,
                    NotificationChannel.IN_APP,
                    WorkspaceNotificationPolicyMode.FORCE_ENABLED,
                    "collab",
                    user,
                    user,
                    Instant.now(),
                    Instant.now())));
    var resolver = resolver(true, true);
    assertThat(
            resolver.isChannelEnabled(
                user, ws, UserNotificationType.COMMENT_ADDED, NotificationChannel.IN_APP))
        .isTrue();
  }

  @Test
  void forceDisabledOverridesUserEnabledPreference() {
    UUID user = UUID.randomUUID();
    UUID ws = UUID.randomUUID();
    when(global.isEnabled(
            eq(user), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(true);
    when(workspaceRepo.findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
            eq(user), eq(ws), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(Optional.empty());
    when(policyRepo.findByWorkspaceIdAndNotificationTypeAndChannel(
            eq(ws), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.EMAIL)))
        .thenReturn(
            Optional.of(
                new WorkspaceNotificationPolicy(
                    UUID.randomUUID(),
                    ws,
                    UserNotificationType.COMMENT_ADDED,
                    NotificationChannel.EMAIL,
                    WorkspaceNotificationPolicyMode.FORCE_DISABLED,
                    "quiet",
                    user,
                    user,
                    Instant.now(),
                    Instant.now())));
    var resolver = resolver(true, true);
    assertThat(
            resolver.isChannelEnabled(
                user, ws, UserNotificationType.COMMENT_ADDED, NotificationChannel.EMAIL))
        .isFalse();
    assertThat(
            resolver.classifyChannelDisabledAnalyticsReason(
                user, ws, UserNotificationType.COMMENT_ADDED, NotificationChannel.EMAIL))
        .isEqualTo(NotificationAnalyticsEventKind.SKIPPED_WORKSPACE_ADMIN_POLICY);
  }

  @Test
  void policiesDisabledIgnoresStoredPolicyRow() {
    UUID user = UUID.randomUUID();
    UUID ws = UUID.randomUUID();
    when(global.isEnabled(
            eq(user), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.IN_APP)))
        .thenReturn(true);
    when(workspaceRepo.findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
            eq(user), eq(ws), eq(UserNotificationType.COMMENT_ADDED), eq(NotificationChannel.IN_APP)))
        .thenReturn(
            Optional.of(
                new UserWorkspaceNotificationPreference(
                    UUID.randomUUID(),
                    user,
                    ws,
                    UserNotificationType.COMMENT_ADDED,
                    NotificationChannel.IN_APP,
                    false,
                    Instant.now())));
    var resolver = resolver(true, false);
    assertThat(
            resolver.isChannelEnabled(
                user, ws, UserNotificationType.COMMENT_ADDED, NotificationChannel.IN_APP))
        .isFalse();
  }
}
