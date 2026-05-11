package com.notebook.lumen.identity.admin.gitops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.identity.admin.changerequest.AdminOperationRegistry;
import com.notebook.lumen.identity.admin.changerequest.ChangeRequestStatus;
import com.notebook.lumen.identity.admin.changerequest.PlatformAdminChangeRequest;
import com.notebook.lumen.identity.admin.changerequest.PlatformAdminChangeRequestRepository;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos.CreatePrBody;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos.DryRunBody;
import com.notebook.lumen.identity.audit.AuditService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminGitOpsPrServiceTest {

  @Mock PlatformAdminChangeRequestRepository changeRequestRepository;
  @Mock AdminGitOpsPrProposalRepository proposalRepository;
  @Mock GitOpsPullRequestProviderRegistry providerRegistry;
  @Mock GitOpsPullRequestProvider mockProvider;
  @Mock AuditService auditService;

  final AdminGitOpsPrProperties propsEnabled =
      new AdminGitOpsPrProperties(
          true,
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

  final AdminGitOpsPrProperties propsEnabledRbacGitOps =
      new AdminGitOpsPrProperties(
          true,
          "mock",
          "dev,staging,prod",
          "staging",
          "main",
          "admin-change",
          "",
          "",
          "",
          true,
          true);
  final GitOpsYamlPatchService patchService = new GitOpsYamlPatchService();
  final AdminOperationRegistry registry = new AdminOperationRegistry();

  AdminGitOpsPrService service;

  private void resetService(AdminGitOpsPrProperties props) {
    service =
        new AdminGitOpsPrService(
            props,
            patchService,
            changeRequestRepository,
            proposalRepository,
            providerRegistry,
            registry,
            auditService);
  }

  @BeforeEach
  void setUp() {
    resetService(propsEnabled);
    when(mockProvider.getProviderName()).thenReturn("mock");
    when(providerRegistry.requireProvider()).thenReturn(mockProvider);
    when(mockProvider.createPullRequest(any(), any()))
        .thenReturn(
            new GitOpsPrProviderResult("https://mock.example/pr/1", "1", "admin-change/x-staging"));
  }

  @Test
  void dryRun_pendingRejectedWhenRequireApproved() {
    UUID id = UUID.randomUUID();
    when(changeRequestRepository.findById(id))
        .thenReturn(Optional.of(approvedLike(id, ChangeRequestStatus.PENDING)));
    assertThatThrownBy(
            () ->
                service.dryRun(
                    id, new DryRunBody("staging"), UUID.randomUUID(), new MockHttpServletRequest()))
        .isInstanceOf(AdminGitOpsException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_GITOPS_CHANGE_REQUEST_NOT_APPROVED");
  }

  @Test
  void dryRun_approved_ok() {
    UUID id = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    when(changeRequestRepository.findById(id))
        .thenReturn(Optional.of(approvedLike(id, ChangeRequestStatus.APPROVED)));
    AdminGitOpsDtos.DryRunResponse r =
        service.dryRun(id, new DryRunBody(null), actor, new MockHttpServletRequest());
    assertThat(r.changeRequestId()).isEqualTo(id);
    assertThat(r.targetEnvironment()).isEqualTo("staging");
    assertThat(r.changedFiles()).hasSize(1);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
    verify(auditService)
        .record(eq("ADMIN_GITOPS_DRY_RUN_CREATED"), eq(actor), any(), eq(id), any(), cap.capture());
    assertThat(cap.getValue()).containsKey("changeRequestId");
  }

  @Test
  void createPr_pendingRejected() {
    UUID id = UUID.randomUUID();
    when(changeRequestRepository.findById(id))
        .thenReturn(Optional.of(approvedLike(id, ChangeRequestStatus.PENDING)));
    assertThatThrownBy(
            () ->
                service.createPr(
                    id,
                    new CreatePrBody("staging", null, null),
                    UUID.randomUUID(),
                    new MockHttpServletRequest()))
        .isInstanceOf(AdminGitOpsException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_GITOPS_CHANGE_REQUEST_NOT_APPROVED");
  }

  @Test
  void createPr_envMismatchRejected() {
    UUID id = UUID.randomUUID();
    when(changeRequestRepository.findById(id))
        .thenReturn(Optional.of(approvedLike(id, ChangeRequestStatus.APPROVED)));
    assertThatThrownBy(
            () ->
                service.createPr(
                    id,
                    new CreatePrBody("prod", null, "CONFIRM"),
                    UUID.randomUUID(),
                    new MockHttpServletRequest()))
        .isInstanceOf(AdminGitOpsException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_GITOPS_ENVIRONMENT_NOT_ALLOWED");
  }

  @Test
  void dryRun_rbacGitOpsDisabled_rejected() {
    UUID targetUser = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    when(changeRequestRepository.findById(id))
        .thenReturn(Optional.of(rbacApprovedCr(id, targetUser, ChangeRequestStatus.APPROVED)));
    assertThatThrownBy(
            () ->
                service.dryRun(
                    id, new DryRunBody("staging"), UUID.randomUUID(), new MockHttpServletRequest()))
        .isInstanceOf(AdminGitOpsException.class)
        .hasFieldOrPropertyWithValue("errorCode", "ADMIN_GITOPS_RBAC_DISABLED");
  }

  @Test
  void dryRun_rbacApproved_targetsOverridesFile() {
    resetService(propsEnabledRbacGitOps);
    UUID targetUser = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    when(changeRequestRepository.findById(id))
        .thenReturn(Optional.of(rbacApprovedCr(id, targetUser, ChangeRequestStatus.APPROVED)));
    AdminGitOpsDtos.DryRunResponse r =
        service.dryRun(id, new DryRunBody(null), actor, new MockHttpServletRequest());
    assertThat(r.changedFiles().get(0).path()).endsWith("admin-rbac-overrides.yaml");
    // unifiedDiffPreview omits unchanged lines; the adminRbacOverrides key line may not appear when
    // only assignments[] grows.
    assertThat(r.diffPreview()).contains("assignments");
    assertThat(r.diffPreview()).contains(targetUser.toString());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
    verify(auditService)
        .record(eq("ADMIN_GITOPS_DRY_RUN_CREATED"), eq(actor), any(), eq(id), any(), cap.capture());
    assertThat(cap.getValue()).containsEntry("targetUserId", targetUser.toString());
  }

  @Test
  void createPr_rbacGitOps_mockSuccess() {
    resetService(propsEnabledRbacGitOps);
    UUID targetUser = UUID.randomUUID();
    UUID id = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    when(changeRequestRepository.findById(id))
        .thenReturn(Optional.of(rbacApprovedCr(id, targetUser, ChangeRequestStatus.APPROVED)));
    when(proposalRepository.findFirstByChangeRequestIdAndTargetEnvironmentAndProviderAndStatus(
            id, "staging", "mock", GitOpsPrProposalStatus.PR_CREATED))
        .thenReturn(Optional.empty());
    AdminGitOpsDtos.CreatePrResponse resp =
        service.createPr(
            id,
            new CreatePrBody("staging", UUID.randomUUID().toString(), null),
            actor,
            new MockHttpServletRequest());
    assertThat(resp.status()).isEqualTo("PR_CREATED");
    verify(auditService)
        .record(eq("ADMIN_GITOPS_PR_CREATED"), eq(actor), any(), any(), any(), any());
    verify(auditService)
        .record(eq("ADMIN_RBAC_GITOPS_PROPOSAL_CREATED"), eq(actor), any(), any(), any(), any());
  }

  @Test
  void createPr_mockProvider_success() {
    UUID id = UUID.randomUUID();
    UUID actor = UUID.randomUUID();
    when(changeRequestRepository.findById(id))
        .thenReturn(Optional.of(approvedLike(id, ChangeRequestStatus.APPROVED)));
    when(proposalRepository.findFirstByChangeRequestIdAndTargetEnvironmentAndProviderAndStatus(
            id, "staging", "mock", GitOpsPrProposalStatus.PR_CREATED))
        .thenReturn(Optional.empty());
    AdminGitOpsDtos.CreatePrResponse resp =
        service.createPr(
            id,
            new CreatePrBody("staging", UUID.randomUUID().toString(), null),
            actor,
            new MockHttpServletRequest());
    assertThat(resp.status()).isEqualTo("PR_CREATED");
    assertThat(resp.providerPrUrl()).isNotBlank();
    verify(proposalRepository, times(2)).save(any(AdminGitOpsPrProposal.class));
    verify(auditService)
        .record(eq("ADMIN_GITOPS_PR_CREATED"), eq(actor), any(), any(), any(), any());
  }

  private static PlatformAdminChangeRequest approvedLike(UUID id, ChangeRequestStatus status) {
    return new PlatformAdminChangeRequest(
        id,
        UUID.randomUUID(),
        null,
        AdminOperationRegistry.OP_MERGE_ANALYSIS_ROLLOUT_REQUEST,
        "content-service",
        "NOTE_MERGE_ANALYSIS_ENABLED",
        null,
        "true",
        "staging",
        status,
        Map.of(),
        Map.of(),
        null,
        Instant.now(),
        "MEDIUM");
  }

  private static PlatformAdminChangeRequest rbacApprovedCr(
      UUID id, UUID targetUser, ChangeRequestStatus status) {
    String normalized = "grant:platform_audit_viewer:" + targetUser;
    Map<String, Object> validation =
        Map.of(
            "valid",
            true,
            "targetUserId",
            targetUser.toString(),
            "requestedRole",
            "PLATFORM_AUDIT_VIEWER",
            "rbacAction",
            "GRANT");
    return new PlatformAdminChangeRequest(
        id,
        UUID.randomUUID(),
        null,
        AdminOperationRegistry.OP_ADMIN_RBAC_ROLE_GRANT_REQUEST,
        "identity-service",
        "admin.rbac.role",
        null,
        normalized,
        "staging",
        status,
        Map.of(),
        validation,
        null,
        Instant.now(),
        "MEDIUM");
  }
}
