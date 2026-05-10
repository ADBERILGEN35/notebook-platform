package com.notebook.lumen.identity.admin.gitops;

import com.notebook.lumen.identity.admin.changerequest.AdminOperationDefinition;
import com.notebook.lumen.identity.admin.changerequest.AdminOperationRegistry;
import com.notebook.lumen.identity.admin.changerequest.ChangeRequestStatus;
import com.notebook.lumen.identity.admin.changerequest.PlatformAdminChangeRequest;
import com.notebook.lumen.identity.admin.changerequest.PlatformAdminChangeRequestRepository;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos.CreatePrBody;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos.CreatePrResponse;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos.DryRunBody;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos.DryRunResponse;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos.FileChangePreview;
import com.notebook.lumen.identity.admin.gitops.api.AdminGitOpsDtos.PathChange;
import com.notebook.lumen.identity.admin.gitops.GitOpsYamlPatchService.PatchPlan;
import com.notebook.lumen.identity.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminGitOpsPrService {

  private static final String CONFIRM = "CONFIRM";
  private static final String NEXT_STEP =
      "Review and merge the PR through your GitOps process. Runtime apply is not performed by this action.";

  private final AdminGitOpsPrProperties gitopsProps;
  private final GitOpsYamlPatchService patchService;
  private final PlatformAdminChangeRequestRepository changeRequestRepository;
  private final AdminGitOpsPrProposalRepository proposalRepository;
  private final GitOpsPullRequestProviderRegistry providerRegistry;
  private final AdminOperationRegistry operationRegistry;
  private final AuditService auditService;

  public AdminGitOpsPrService(
      AdminGitOpsPrProperties gitopsProps,
      GitOpsYamlPatchService patchService,
      PlatformAdminChangeRequestRepository changeRequestRepository,
      AdminGitOpsPrProposalRepository proposalRepository,
      GitOpsPullRequestProviderRegistry providerRegistry,
      AdminOperationRegistry operationRegistry,
      AuditService auditService) {
    this.gitopsProps = gitopsProps;
    this.patchService = patchService;
    this.changeRequestRepository = changeRequestRepository;
    this.proposalRepository = proposalRepository;
    this.providerRegistry = providerRegistry;
    this.operationRegistry = operationRegistry;
    this.auditService = auditService;
  }

  public DryRunResponse dryRun(
      UUID changeRequestId, DryRunBody body, UUID actorUserId, HttpServletRequest request) {
    ensureGitOpsEnabled();
    PlatformAdminChangeRequest cr = loadChangeRequest(changeRequestId);
    ensureApprovedIfRequired(cr);
    String env = resolvePreviewEnvironment(cr, body == null ? null : body.targetEnvironment());
    gitopsProps.validateEnvironment(env);
    AdminOperationDefinition def = resolveOperation(cr.getOperationType());
    String normalized = operationRegistry.normalizeValue(def, cr.getRequestedValue());
    PatchPlan plan = patchService.buildPatchPlan(def.operationType(), normalized, env);

    List<FileChangePreview> files =
        List.of(
            new FileChangePreview(
                plan.relativePath(),
                List.of(new PathChange(plan.yamlDotPath(), plan.oldValue(), plan.newValue()))));

    auditService.record(
        "ADMIN_GITOPS_DRY_RUN_CREATED",
        actorUserId,
        "ADMIN_GITOPS_PR_PROPOSAL",
        changeRequestId,
        request,
        dryRunAudit(cr, env, plan.relativePath()));

    return new DryRunResponse(
        changeRequestId,
        env,
        gitopsProps.provider(),
        files,
        plan.diffPreview(),
        List.of());
  }

  @Transactional
  public CreatePrResponse createPr(
      UUID changeRequestId, CreatePrBody body, UUID actorUserId, HttpServletRequest request) {
    ensureGitOpsEnabled();
    if (body == null || body.targetEnvironment() == null || body.targetEnvironment().isBlank()) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_ENVIRONMENT_NOT_ALLOWED", HttpStatus.BAD_REQUEST, "targetEnvironment is required");
    }
    PlatformAdminChangeRequest cr = loadChangeRequest(changeRequestId);
    ensureApprovedIfRequired(cr);
    String env = body.targetEnvironment().trim().toLowerCase(Locale.ROOT);
    gitopsProps.validateEnvironment(env);
    if (!cr.getTargetEnvironment().trim().equalsIgnoreCase(env)) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_ENVIRONMENT_NOT_ALLOWED",
          HttpStatus.BAD_REQUEST,
          "targetEnvironment must match the change request target environment");
    }
    if ("prod".equals(env) && "HIGH".equalsIgnoreCase(cr.getSeverity())) {
      if (body.confirmation() == null || !CONFIRM.equals(body.confirmation().trim())) {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_PATCH_FAILED",
            HttpStatus.BAD_REQUEST,
            "Production GitOps PR for HIGH severity requires confirmation: CONFIRM");
      }
    }

    GitOpsPullRequestProvider provider = providerRegistry.requireProvider();
    String providerName = provider.getProviderName();

    Optional<AdminGitOpsPrProposal> existingSuccess =
        proposalRepository.findFirstByChangeRequestIdAndTargetEnvironmentAndProviderAndStatus(
            changeRequestId, env, providerName, GitOpsPrProposalStatus.PR_CREATED);
    if (existingSuccess.isPresent()) {
      return toCreatePrResponse(existingSuccess.get());
    }

    if (body.idempotencyKey() != null && !body.idempotencyKey().isBlank()) {
      String key = body.idempotencyKey().trim();
      Optional<AdminGitOpsPrProposal> byKey = proposalRepository.findByIdempotencyKey(key);
      if (byKey.isPresent()) {
        AdminGitOpsPrProposal p = byKey.get();
        if (!p.getChangeRequestId().equals(changeRequestId)) {
          throw new AdminGitOpsException(
              "ADMIN_GITOPS_IDEMPOTENCY_KEY_REUSED",
              HttpStatus.CONFLICT,
              "Idempotency key was already used for a different change request");
        }
        if (p.getStatus() == GitOpsPrProposalStatus.PR_CREATED) {
          return toCreatePrResponse(p);
        }
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_IDEMPOTENCY_KEY_REUSED",
            HttpStatus.CONFLICT,
            "Idempotency key is already associated with an in-flight or failed proposal");
      }
    }

    AdminOperationDefinition def = resolveOperation(cr.getOperationType());
    String normalized = operationRegistry.normalizeValue(def, cr.getRequestedValue());
    PatchPlan plan = patchService.buildPatchPlan(def.operationType(), normalized, env);

    String headBranch =
        sanitizeBranchName(
            gitopsProps.branchPrefix() + "/" + cr.getId().toString().substring(0, 8) + "-" + env);
    String title = "Admin: " + cr.getOperationType() + " (" + env + ")";
    String prBody = buildPrBody(cr, env, normalized);

    Instant now = Instant.now();
    Map<String, Object> changedFilesPayload =
        Map.of(
            "files",
            List.of(
                Map.of(
                    "path",
                    plan.relativePath(),
                    "changes",
                    List.of(
                        Map.of(
                            "yamlPath",
                            plan.yamlDotPath(),
                            "oldValue",
                            plan.oldValue(),
                            "newValue",
                            plan.newValue())))));
    Map<String, Object> diffSummary = Map.of("diffPreview", plan.diffPreview());

    AdminGitOpsPrProposal proposal =
        new AdminGitOpsPrProposal(
            UUID.randomUUID(),
            changeRequestId,
            GitOpsPrProposalStatus.READY,
            providerName,
            env,
            gitopsProps.baseBranch(),
            null,
            title,
            prBody,
            changedFilesPayload,
            diffSummary,
            null,
            null,
            null,
            actorUserId,
            body.idempotencyKey() == null || body.idempotencyKey().isBlank() ? null : body.idempotencyKey().trim(),
            now,
            null);

    try {
      proposalRepository.save(proposal);
    } catch (DataIntegrityViolationException e) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_IDEMPOTENCY_KEY_REUSED",
          HttpStatus.CONFLICT,
          "Idempotency key conflict — retry with a new key");
    }

    try {
      if ("github".equals(providerName)) {
        provider.validateRepositoryAccess(gitopsProps);
      }
      GitOpsPrProviderRequest prReq =
          new GitOpsPrProviderRequest(
              gitopsProps.repositoryOwner(),
              gitopsProps.repositoryName(),
              gitopsProps.baseBranch(),
              headBranch,
              plan.relativePath(),
              "Admin GitOps: " + cr.getOperationType(),
              title,
              prBody,
              def.operationType(),
              normalized);
      GitOpsPrProviderResult result = provider.createPullRequest(prReq, gitopsProps);
      proposal.setBranchName(result.headBranch());
      proposal.setProviderPrUrl(result.providerPrUrl());
      proposal.setProviderPrNumber(result.providerPrNumber());
      proposal.setStatus(GitOpsPrProposalStatus.PR_CREATED);
      proposal.setPrCreatedAt(Instant.now());
      proposalRepository.save(proposal);
      auditService.record(
          "ADMIN_GITOPS_PR_CREATED",
          actorUserId,
          "ADMIN_GITOPS_PR_PROPOSAL",
          proposal.getId(),
          request,
          prAudit(cr, env, providerName, plan.relativePath(), result.headBranch(), result.providerPrUrl()));
      return toCreatePrResponse(proposal);
    } catch (AdminGitOpsException e) {
      proposal.setStatus(GitOpsPrProposalStatus.FAILED);
      proposal.setLastError(e.getMessage());
      proposalRepository.save(proposal);
      if ("ADMIN_GITOPS_PROVIDER_VALIDATION_FAILED".equals(e.getErrorCode())) {
        auditService.record(
            "ADMIN_GITOPS_PROVIDER_VALIDATION_FAILED",
            actorUserId,
            "ADMIN_GITOPS_PR_PROPOSAL",
            proposal.getId(),
            request,
            prFailAudit(cr, env, providerName, e.getErrorCode()));
      } else {
        auditService.record(
            "ADMIN_GITOPS_PR_CREATION_FAILED",
            actorUserId,
            "ADMIN_GITOPS_PR_PROPOSAL",
            proposal.getId(),
            request,
            prFailAudit(cr, env, providerName, e.getErrorCode()));
      }
      throw e;
    } catch (RuntimeException e) {
      proposal.setStatus(GitOpsPrProposalStatus.FAILED);
      proposal.setLastError("unexpected_error");
      proposalRepository.save(proposal);
      auditService.record(
          "ADMIN_GITOPS_PR_CREATION_FAILED",
          actorUserId,
          "ADMIN_GITOPS_PR_PROPOSAL",
          proposal.getId(),
          request,
          prFailAudit(cr, env, providerName, "UNEXPECTED"));
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PROVIDER_FAILED",
          HttpStatus.BAD_GATEWAY,
          "Unexpected error while creating GitOps pull request");
    }
  }

  private CreatePrResponse toCreatePrResponse(AdminGitOpsPrProposal p) {
    return new CreatePrResponse(
        p.getId(),
        p.getStatus().name(),
        p.getProvider(),
        p.getProviderPrUrl(),
        p.getBranchName(),
        NEXT_STEP);
  }

  private void ensureGitOpsEnabled() {
    if (!gitopsProps.enabled()) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_DISABLED", HttpStatus.NOT_FOUND, "GitOps PR automation is disabled");
    }
  }

  private void ensureApprovedIfRequired(PlatformAdminChangeRequest cr) {
    if (!gitopsProps.requireApprovedChange()) {
      return;
    }
    if (cr.getStatus() != ChangeRequestStatus.APPROVED) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_CHANGE_REQUEST_NOT_APPROVED",
          HttpStatus.CONFLICT,
          "Change request must be APPROVED before GitOps actions");
    }
  }

  private PlatformAdminChangeRequest loadChangeRequest(UUID id) {
    return changeRequestRepository
        .findById(id)
        .orElseThrow(
            () ->
                new AdminGitOpsException(
                    "ADMIN_CHANGE_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "Change request not found"));
  }

  private AdminOperationDefinition resolveOperation(String operationType) {
    return operationRegistry
        .find(operationType)
        .orElseThrow(
            () ->
                new AdminGitOpsException(
                    "ADMIN_GITOPS_OPERATION_UNSUPPORTED",
                    HttpStatus.BAD_REQUEST,
                    "Operation is not mapped for GitOps"));
  }

  private String resolvePreviewEnvironment(PlatformAdminChangeRequest cr, String bodyEnv) {
    if (bodyEnv != null && !bodyEnv.isBlank()) {
      gitopsProps.validateEnvironment(bodyEnv);
      return bodyEnv.trim().toLowerCase(Locale.ROOT);
    }
    String fromCr = cr.getTargetEnvironment().trim().toLowerCase(Locale.ROOT);
    gitopsProps.validateEnvironment(fromCr);
    return fromCr;
  }

  private static String buildPrBody(PlatformAdminChangeRequest cr, String env, String normalizedValue) {
    return "Approved admin change request — GitOps patch (no secrets).\n\n"
        + "Change-Request-Id: "
        + cr.getId()
        + "\n"
        + "OperationType: "
        + cr.getOperationType()
        + "\n"
        + "TargetEnvironment: "
        + env
        + "\n"
        + "Target: "
        + cr.getTargetService()
        + ":"
        + cr.getTargetKey()
        + "\n"
        + "RequestedValue: "
        + normalizedValue
        + "\n";
  }

  private static Map<String, Object> dryRunAudit(PlatformAdminChangeRequest cr, String env, String path) {
    Map<String, Object> m = new HashMap<>();
    m.put("changeRequestId", cr.getId().toString());
    m.put("operationType", cr.getOperationType());
    m.put("targetEnvironment", env);
    m.put("changedFilePaths", List.of(path));
    return m;
  }

  private static Map<String, Object> prAudit(
      PlatformAdminChangeRequest cr,
      String env,
      String provider,
      String path,
      String branch,
      String prUrl) {
    Map<String, Object> m = new HashMap<>();
    m.put("changeRequestId", cr.getId().toString());
    m.put("operationType", cr.getOperationType());
    m.put("targetEnvironment", env);
    m.put("provider", provider);
    m.put("changedFilePaths", path == null ? List.of() : List.of(path));
    m.put("branchName", branch == null ? "" : branch);
    m.put("prUrl", prUrl == null ? "" : prUrl);
    return m;
  }

  private static Map<String, Object> prFailAudit(
      PlatformAdminChangeRequest cr, String env, String provider, String code) {
    Map<String, Object> m = new HashMap<>();
    m.put("changeRequestId", cr.getId().toString());
    m.put("operationType", cr.getOperationType());
    m.put("targetEnvironment", env);
    m.put("provider", provider);
    m.put("errorCode", code == null ? "" : code);
    return m;
  }

  private static String sanitizeBranchName(String raw) {
    String s = raw.replaceAll("[^a-zA-Z0-9/_-]", "-");
    if (s.length() > 240) {
      s = s.substring(0, 240);
    }
    return s;
  }
}
