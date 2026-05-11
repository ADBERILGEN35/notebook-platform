package com.notebook.lumen.identity.admin.changerequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.admin.changerequest.api.AdminChangeRequestDtos;
import com.notebook.lumen.identity.admin.gitops.AdminGitOpsPrProperties;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AdminChangeRequestServiceTest {

  @Mock PlatformAdminChangeRequestRepository changeRequestRepository;
  @Mock UserRepository userRepository;
  @Mock AuditService auditService;
  @Mock AdminRbacRoleChangeRequestValidator rbacRoleChangeRequestValidator;

  final AdminOperationRegistry registry = new AdminOperationRegistry();
  final AdminGitOpsPrProperties gitOpsProps =
      new AdminGitOpsPrProperties(
          false,
          "mock",
          "dev,staging,prod",
          "staging",
          "main",
          "admin-change",
          "",
          "",
          "",
          true,
          false);
  AdminChangeRequestProperties properties =
      new AdminChangeRequestProperties(
          true,
          180,
          new AdminChangeRequestProperties.Approvals(true, true, true, true, false, true));
  AdminChangeRequestService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminChangeRequestService(
            properties,
            gitOpsProps,
            registry,
            rbacRoleChangeRequestValidator,
            changeRequestRepository,
            userRepository,
            auditService);
  }

  @Test
  void validate_rejectsUnknownOperation() {
    assertThatThrownBy(
            () ->
                service.validate(
                    new AdminChangeRequestDtos.ValidateBody(
                        "NOT_ALLOWED", "true", null, null, null)))
        .isInstanceOf(AdminChangeRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_OPERATION_NOT_ALLOWED");
  }

  @Test
  void validate_normalizesAndReturnsImpact() {
    AdminChangeRequestDtos.ValidateResponse r =
        service.validate(
            new AdminChangeRequestDtos.ValidateBody(
                AdminOperationRegistry.OP_MERGE_ANALYSIS_ROLLOUT_REQUEST, "YES", null, null, null));
    assertThat(r.valid()).isTrue();
    assertThat(r.requiresApproval()).isTrue();
    assertThat(r.impactSummary().get("severity")).isEqualTo("MEDIUM");
    assertThat(r.validationResult().get("normalizedRequestedValue")).isEqualTo("true");
  }

  @Test
  void create_highSeverityRequiresConfirmPhrase() {
    UUID uid = UUID.randomUUID();
    when(userRepository.existsById(uid)).thenReturn(true);
    HttpServletRequest req = new MockHttpServletRequest();
    assertThatThrownBy(
            () ->
                service.create(
                    uid,
                    "a@b.com",
                    new AdminChangeRequestDtos.CreateBody(
                        AdminOperationRegistry.OP_MERGE_APPLY_ROLLOUT_REQUEST,
                        "true",
                        null,
                        null,
                        "staging",
                        null),
                    "rid",
                    req))
        .isInstanceOf(AdminChangeRequestException.class)
        .hasFieldOrPropertyWithValue("httpStatus", HttpStatus.BAD_REQUEST);
  }

  @Test
  void create_persistsAndAudits() {
    UUID uid = UUID.randomUUID();
    when(userRepository.existsById(uid)).thenReturn(true);
    HttpServletRequest req = new MockHttpServletRequest();
    service.create(
        uid,
        "a@b.com",
        new AdminChangeRequestDtos.CreateBody(
            AdminOperationRegistry.OP_MERGE_ANALYSIS_ROLLOUT_REQUEST,
            "false",
            null,
            null,
            "staging",
            null),
        "rid",
        req);
    verify(changeRequestRepository).save(any());
    verify(auditService)
        .record(
            eq("ADMIN_CHANGE_REQUEST_CREATED"),
            eq(uid),
            eq("ADMIN_CHANGE_REQUEST"),
            any(),
            any(),
            any());
  }

  @Test
  void approve_pending_success() {
    UUID creator = UUID.randomUUID();
    UUID approver = UUID.randomUUID();
    UUID rid = UUID.randomUUID();
    PlatformAdminChangeRequest row = pendingRow(rid, creator);
    when(changeRequestRepository.findById(rid)).thenReturn(Optional.of(row));
    when(userRepository.existsById(approver)).thenReturn(true);
    AdminChangeRequestDtos.ApproveResponse r =
        service.approve(
            rid,
            approver,
            new AdminChangeRequestDtos.DecisionBody("ok"),
            new MockHttpServletRequest());
    assertThat(r.status()).isEqualTo("APPROVED");
    assertThat(r.nextStep().get("type")).isEqualTo("GITOPS_OR_MANUAL_APPLY");
    verify(changeRequestRepository).save(any());
    verify(auditService)
        .record(eq("ADMIN_CHANGE_REQUEST_APPROVED"), eq(approver), any(), any(), any(), any());
  }

  @Test
  void approve_selfBlocked_whenPolicyRequiresDifferentApprover() {
    UUID uid = UUID.randomUUID();
    UUID rid = UUID.randomUUID();
    PlatformAdminChangeRequest row = pendingRow(rid, uid);
    when(changeRequestRepository.findById(rid)).thenReturn(Optional.of(row));
    when(userRepository.existsById(uid)).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.approve(
                    rid,
                    uid,
                    new AdminChangeRequestDtos.DecisionBody("x"),
                    new MockHttpServletRequest()))
        .isInstanceOf(AdminChangeRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_CHANGE_REQUEST_SELF_APPROVAL_NOT_ALLOWED");
    verify(auditService)
        .record(eq("ADMIN_CHANGE_REQUEST_APPROVAL_DENIED"), eq(uid), any(), any(), any(), any());
  }

  @Test
  void approve_selfAllowed_whenPolicyOff() {
    properties =
        new AdminChangeRequestProperties(
            true,
            180,
            new AdminChangeRequestProperties.Approvals(true, false, true, true, false, true));
    service =
        new AdminChangeRequestService(
            properties,
            gitOpsProps,
            registry,
            rbacRoleChangeRequestValidator,
            changeRequestRepository,
            userRepository,
            auditService);
    UUID uid = UUID.randomUUID();
    UUID rid = UUID.randomUUID();
    PlatformAdminChangeRequest row = pendingRow(rid, uid);
    when(changeRequestRepository.findById(rid)).thenReturn(Optional.of(row));
    when(userRepository.existsById(uid)).thenReturn(true);
    service.approve(rid, uid, null, new MockHttpServletRequest());
    verify(changeRequestRepository).save(any());
  }

  @Test
  void reject_highSeverity_requiresReason() {
    UUID creator = UUID.randomUUID();
    UUID approver = UUID.randomUUID();
    UUID rid = UUID.randomUUID();
    PlatformAdminChangeRequest row = highPendingRow(rid, creator);
    when(changeRequestRepository.findById(rid)).thenReturn(Optional.of(row));
    when(userRepository.existsById(approver)).thenReturn(true);
    assertThatThrownBy(
            () ->
                service.reject(
                    rid,
                    approver,
                    new AdminChangeRequestDtos.DecisionBody(""),
                    new MockHttpServletRequest()))
        .isInstanceOf(AdminChangeRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_CHANGE_REQUEST_REJECT_REASON_REQUIRED");
  }

  @Test
  void reject_pending_success() {
    UUID creator = UUID.randomUUID();
    UUID approver = UUID.randomUUID();
    UUID rid = UUID.randomUUID();
    PlatformAdminChangeRequest row = highPendingRow(rid, creator);
    when(changeRequestRepository.findById(rid)).thenReturn(Optional.of(row));
    when(userRepository.existsById(approver)).thenReturn(true);
    AdminChangeRequestDtos.RejectResponse r =
        service.reject(
            rid,
            approver,
            new AdminChangeRequestDtos.DecisionBody("not yet"),
            new MockHttpServletRequest());
    assertThat(r.status()).isEqualTo("REJECTED");
    verify(changeRequestRepository).save(any());
  }

  @Test
  void approve_whenApprovalsDisabled_throws() {
    properties =
        new AdminChangeRequestProperties(
            true,
            180,
            new AdminChangeRequestProperties.Approvals(false, true, true, true, false, true));
    service =
        new AdminChangeRequestService(
            properties,
            gitOpsProps,
            registry,
            rbacRoleChangeRequestValidator,
            changeRequestRepository,
            userRepository,
            auditService);
    UUID rid = UUID.randomUUID();
    UUID approver = UUID.randomUUID();
    assertThatThrownBy(() -> service.approve(rid, approver, null, new MockHttpServletRequest()))
        .isInstanceOf(AdminChangeRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_CHANGE_REQUEST_APPROVAL_DISABLED");
  }

  @Test
  void cancel_onlyPending() {
    UUID uid = UUID.randomUUID();
    UUID rid = UUID.randomUUID();
    PlatformAdminChangeRequest row =
        new PlatformAdminChangeRequest(
            rid,
            uid,
            null,
            AdminOperationRegistry.OP_MERGE_ANALYSIS_ROLLOUT_REQUEST,
            "content-service",
            "NOTE_MERGE_ANALYSIS_ENABLED",
            null,
            "true",
            "staging",
            ChangeRequestStatus.APPLIED,
            java.util.Map.of(),
            java.util.Map.of(),
            null,
            java.time.Instant.now(),
            "MEDIUM");
    when(changeRequestRepository.findByIdAndRequestedByUserId(rid, uid))
        .thenReturn(Optional.of(row));
    assertThatThrownBy(() -> service.cancel(rid, uid, false, new MockHttpServletRequest()))
        .isInstanceOf(AdminChangeRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_CHANGE_REQUEST_NOT_CANCELLABLE");
  }

  @Test
  void cancel_pending_updatesRow() {
    UUID uid = UUID.randomUUID();
    UUID rid = UUID.randomUUID();
    PlatformAdminChangeRequest row =
        new PlatformAdminChangeRequest(
            rid,
            uid,
            null,
            AdminOperationRegistry.OP_MERGE_ANALYSIS_ROLLOUT_REQUEST,
            "content-service",
            "NOTE_MERGE_ANALYSIS_ENABLED",
            null,
            "true",
            "staging",
            ChangeRequestStatus.PENDING,
            java.util.Map.of(),
            java.util.Map.of(),
            null,
            java.time.Instant.now(),
            "MEDIUM");
    when(changeRequestRepository.findByIdAndRequestedByUserId(rid, uid))
        .thenReturn(Optional.of(row));
    service.cancel(rid, uid, false, new MockHttpServletRequest());
    ArgumentCaptor<PlatformAdminChangeRequest> cap =
        ArgumentCaptor.forClass(PlatformAdminChangeRequest.class);
    verify(changeRequestRepository).save(cap.capture());
    assertThat(cap.getValue().getStatus()).isEqualTo(ChangeRequestStatus.CANCELLED);
    verify(auditService)
        .record(
            eq("ADMIN_CHANGE_REQUEST_CANCELLED"),
            eq(uid),
            eq("ADMIN_CHANGE_REQUEST"),
            any(),
            any(),
            any());
  }

  @Test
  void whenDisabled_validateThrows() {
    properties =
        new AdminChangeRequestProperties(
            false, 180, AdminChangeRequestProperties.Approvals.defaults());
    service =
        new AdminChangeRequestService(
            properties,
            gitOpsProps,
            registry,
            rbacRoleChangeRequestValidator,
            changeRequestRepository,
            userRepository,
            auditService);
    assertThatThrownBy(
            () ->
                service.validate(
                    new AdminChangeRequestDtos.ValidateBody(
                        AdminOperationRegistry.OP_MERGE_ANALYSIS_ROLLOUT_REQUEST,
                        "true",
                        null,
                        null,
                        null)))
        .isInstanceOf(AdminChangeRequestException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_WRITE_DISABLED");
    verify(changeRequestRepository, never()).save(any());
  }

  private static PlatformAdminChangeRequest pendingRow(UUID id, UUID creator) {
    return new PlatformAdminChangeRequest(
        id,
        creator,
        null,
        AdminOperationRegistry.OP_MERGE_ANALYSIS_ROLLOUT_REQUEST,
        "content-service",
        "NOTE_MERGE_ANALYSIS_ENABLED",
        null,
        "true",
        "staging",
        ChangeRequestStatus.PENDING,
        java.util.Map.of("severity", "MEDIUM"),
        java.util.Map.of(),
        null,
        java.time.Instant.now(),
        "MEDIUM");
  }

  private static PlatformAdminChangeRequest highPendingRow(UUID id, UUID creator) {
    return new PlatformAdminChangeRequest(
        id,
        creator,
        null,
        AdminOperationRegistry.OP_MERGE_APPLY_ROLLOUT_REQUEST,
        "content-service",
        "NOTE_MERGE_APPLY_ENABLED",
        null,
        "true",
        "staging",
        ChangeRequestStatus.PENDING,
        java.util.Map.of("severity", "HIGH"),
        java.util.Map.of(),
        null,
        java.time.Instant.now(),
        "HIGH");
  }
}
