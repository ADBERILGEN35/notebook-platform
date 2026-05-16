package com.notebook.lumen.notification.admin.platformretention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionDtos.NotificationPlatformRetentionPlanResponse;
import com.notebook.lumen.notification.admin.platformretention.NotificationPlatformRetentionDtos.NotificationPlatformRetentionTargetView;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class InternalNotificationPlatformRetentionControllerTest {

  private final InternalNotificationAuthorizer authorizer =
      mock(InternalNotificationAuthorizer.class);
  private final NotificationPlatformRetentionPlanService planService =
      mock(NotificationPlatformRetentionPlanService.class);
  private final InternalNotificationPlatformRetentionController controller =
      new InternalNotificationPlatformRetentionController(authorizer, planService);

  private NotificationPlatformRetentionPlanResponse sampleResponse() {
    return new NotificationPlatformRetentionPlanResponse(
        "notification-service",
        true,
        Instant.parse("2026-05-16T00:00:00Z"),
        List.of(
            new NotificationPlatformRetentionTargetView(
                "notification.analytics_hourly",
                NotificationPlatformRetentionTargetStatus.DRY_RUN_READY,
                90,
                Instant.parse("2026-02-15T00:00:00Z"),
                12L,
                12,
                false,
                List.of())),
        List.of());
  }

  @Test
  void validRequest_authorizesWithReadScopeAndReturnsPlan() {
    when(planService.buildPlan(any(), any(), any())).thenReturn(sampleResponse());

    var response = controller.plan("Bearer token", true, null, null, null);

    assertThat(response.service()).isEqualTo("notification-service");
    assertThat(response.dryRun()).isTrue();
    verify(authorizer)
        .authorize(
            eq("Bearer token"), eq(InternalNotificationAuthorizer.ADMIN_RETENTION_READ_SCOPE));
  }

  @Test
  void dryRunFalse_returns400() {
    assertThatThrownBy(() -> controller.plan("Bearer token", false, null, null, null))
        .isInstanceOf(NotificationException.class)
        .satisfies(
            e -> {
              NotificationException ne = (NotificationException) e;
              assertThat(ne.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(ne.getErrorCode()).isEqualTo("RETENTION_DRY_RUN_ONLY");
            });
  }

  @Test
  void unknownTarget_returns400() {
    assertThatThrownBy(() -> controller.plan("Bearer token", true, "bogus.target", null, null))
        .isInstanceOf(NotificationException.class)
        .satisfies(
            e -> {
              NotificationException ne = (NotificationException) e;
              assertThat(ne.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(ne.getErrorCode()).isEqualTo("RETENTION_TARGET_UNKNOWN");
            });
  }

  @Test
  void missingServiceJwt_propagatesUnauthorized() {
    doThrow(
            new NotificationException(
                HttpStatus.UNAUTHORIZED, "NOTIFICATION_ACCESS_DENIED", "Service JWT is required"))
        .when(authorizer)
        .authorize(any(), eq(InternalNotificationAuthorizer.ADMIN_RETENTION_READ_SCOPE));

    assertThatThrownBy(() -> controller.plan(null, true, null, null, null))
        .isInstanceOf(NotificationException.class)
        .satisfies(
            e ->
                assertThat(((NotificationException) e).getStatus())
                    .isEqualTo(HttpStatus.UNAUTHORIZED));
    verifyNoInteractions(planService);
  }

  @Test
  void wrongScope_propagatesForbidden() {
    doThrow(
            new NotificationException(
                HttpStatus.FORBIDDEN, "NOTIFICATION_ACCESS_DENIED", "insufficient scope"))
        .when(authorizer)
        .authorize(any(), eq(InternalNotificationAuthorizer.ADMIN_RETENTION_READ_SCOPE));

    assertThatThrownBy(() -> controller.plan("Bearer token", true, null, null, null))
        .isInstanceOf(NotificationException.class)
        .satisfies(
            e ->
                assertThat(((NotificationException) e).getStatus())
                    .isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void legalHoldScopesParsedStrippingSuffixAndSkippingUnknown() {
    when(planService.buildPlan(any(), any(), any())).thenReturn(sampleResponse());

    controller.plan(
        "Bearer token", true, null, "FANOUT_OUTBOX:notification, ,ALL_PLATFORM,bogus", null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Set<NotificationPlatformRetentionLegalHoldScope>> captor =
        ArgumentCaptor.forClass(Set.class);
    verify(planService).buildPlan(any(), captor.capture(), any());
    assertThat(captor.getValue())
        .containsExactlyInAnyOrder(
            NotificationPlatformRetentionLegalHoldScope.FANOUT_OUTBOX,
            NotificationPlatformRetentionLegalHoldScope.ALL_PLATFORM);
  }

  @Test
  void targetFilterResolvedFromKey() {
    when(planService.buildPlan(any(), any(), any())).thenReturn(sampleResponse());

    controller.plan("Bearer token", true, "notification.analytics_hourly", null, null);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Optional<NotificationPlatformRetentionTargetKey>> captor =
        ArgumentCaptor.forClass(Optional.class);
    verify(planService).buildPlan(captor.capture(), any(), any());
    assertThat(captor.getValue())
        .contains(NotificationPlatformRetentionTargetKey.NOTIFICATION_ANALYTICS_HOURLY);
  }

  @Test
  void responseSchema_hasNoPayloadOrEmailBodyFields() throws Exception {
    String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(sampleResponse());

    assertThat(json)
        .doesNotContainIgnoringCase("email")
        .doesNotContainIgnoringCase("recipient")
        .doesNotContainIgnoringCase("subject")
        .doesNotContainIgnoringCase("\"body\"")
        .doesNotContainIgnoringCase("payload")
        .doesNotContainIgnoringCase("workspaceId")
        .doesNotContainIgnoringCase("userId");
    assertThat(json).contains("notification.analytics_hourly", "eligibleCount", "purgeableCount");
  }
}
