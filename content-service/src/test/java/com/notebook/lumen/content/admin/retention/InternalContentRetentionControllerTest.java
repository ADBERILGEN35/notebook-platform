package com.notebook.lumen.content.admin.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.notebook.lumen.content.admin.retention.ContentRetentionCountRepository.CountResult;
import com.notebook.lumen.content.admin.retention.ContentRetentionPlanDtos.ContentRetentionPlanResponse;
import com.notebook.lumen.content.audit.AuditService;
import com.notebook.lumen.content.shared.exception.ContentException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InternalContentRetentionControllerTest {

  private ContentRetentionAdminAuthorizer authorizer;
  private ContentRetentionPlanService planService;
  private InternalContentRetentionController controller;

  @BeforeEach
  void setUp() {
    authorizer = mock(ContentRetentionAdminAuthorizer.class);
    ContentRetentionCountRepository repo = mock(ContentRetentionCountRepository.class);
    when(repo.countNoteVersionsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(10, false));
    when(repo.countCommentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(20, false));
    when(repo.countSearchDocumentsBefore(any(Instant.class), anyInt()))
        .thenReturn(new CountResult(30, false));
    planService =
        new ContentRetentionPlanService(
            new ContentRetentionProperties(true, 100_000, 365, 365, 90, false),
            repo,
            mock(AuditService.class),
            new SimpleMeterRegistry());
    controller = new InternalContentRetentionController(authorizer, planService);
  }

  @Test
  void missingAuth_isRejected() {
    doThrow(new ContentException(HttpStatus.UNAUTHORIZED, "INTERNAL_AUTH_REQUIRED", "x"))
        .when(authorizer)
        .authorize(null);

    assertThatThrownBy(() -> controller.plan(null, true, null, null, null))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("INTERNAL_AUTH_REQUIRED");
  }

  @Test
  void dryRunFalse_rejectedAsBadRequest() {
    doNothing().when(authorizer).authorize("Bearer x");

    assertThatThrownBy(() -> controller.plan("Bearer x", false, null, null, null))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("RETENTION_DRY_RUN_ONLY");
  }

  @Test
  void unknownTarget_rejected() {
    doNothing().when(authorizer).authorize("Bearer x");

    assertThatThrownBy(() -> controller.plan("Bearer x", true, "content.fake", null, null))
        .isInstanceOf(ContentException.class)
        .extracting("errorCode")
        .isEqualTo("RETENTION_TARGET_UNKNOWN");
  }

  @Test
  void validRequest_returnsPlan() {
    doNothing().when(authorizer).authorize("Bearer x");

    ContentRetentionPlanResponse response =
        controller.plan("Bearer x", true, null, "ALL_PLATFORM", null);

    assertThat(response.service()).isEqualTo("content-service");
    assertThat(response.dryRun()).isTrue();
    assertThat(response.targets()).isNotEmpty();
    assertThat(response.targets()).allSatisfy(t -> assertThat(t.blockedByLegalHold()).isTrue());
  }

  @Test
  void scopeWithRef_parsesHeadScopeOnly() {
    doNothing().when(authorizer).authorize("Bearer x");

    ContentRetentionPlanResponse response =
        controller.plan("Bearer x", true, "content.note_versions", "WORKSPACE:abc", null);

    assertThat(response.targets()).hasSize(1);
    assertThat(response.warnings()).contains("CONTENT_RETENTION_PARTIAL_LEGAL_HOLD_MAPPING");
  }
}
