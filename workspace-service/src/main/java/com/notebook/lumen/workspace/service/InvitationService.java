package com.notebook.lumen.workspace.service;

import com.notebook.lumen.workspace.audit.AuditService;
import com.notebook.lumen.workspace.client.IdentityClient;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import com.notebook.lumen.workspace.domain.Invitation;
import com.notebook.lumen.workspace.domain.WorkspaceMember;
import com.notebook.lumen.workspace.domain.WorkspaceRole;
import com.notebook.lumen.workspace.dto.InvitationResponse;
import com.notebook.lumen.workspace.dto.PageResponse;
import com.notebook.lumen.workspace.dto.Requests.AcceptInvitationRequest;
import com.notebook.lumen.workspace.dto.Requests.CreateInvitationRequest;
import com.notebook.lumen.workspace.mapper.WorkspaceMapper;
import com.notebook.lumen.workspace.repository.InvitationRepository;
import com.notebook.lumen.workspace.repository.WorkspaceMemberRepository;
import com.notebook.lumen.workspace.shared.UserContext;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import com.notebook.lumen.workspace.tenant.StrictWorkspaceHeaderValidator;
import com.notebook.lumen.workspace.tenant.TenantDatabaseSession;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {
  public static final Set<String> INVITATION_SORTS = Set.of("createdAt", "expiresAt", "email");

  private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

  private final InvitationRepository invitationRepository;
  private final WorkspaceMemberRepository workspaceMemberRepository;
  private final AuthorizationService authorizationService;
  private final InvitationTokenService tokenService;
  private final IdentityClient identityClient;
  private final CircuitBreaker identityClientCircuitBreaker;
  private final Retry identityClientRetry;
  private final WorkspaceProperties properties;
  private final WorkspaceMapper mapper;
  private final AuditService auditService;
  private final TenantDatabaseSession tenantDatabaseSession;
  private final StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator;

  public InvitationService(
      InvitationRepository invitationRepository,
      WorkspaceMemberRepository workspaceMemberRepository,
      AuthorizationService authorizationService,
      InvitationTokenService tokenService,
      IdentityClient identityClient,
      CircuitBreaker identityClientCircuitBreaker,
      Retry identityClientRetry,
      WorkspaceProperties properties,
      WorkspaceMapper mapper,
      AuditService auditService,
      TenantDatabaseSession tenantDatabaseSession,
      StrictWorkspaceHeaderValidator strictWorkspaceHeaderValidator) {
    this.invitationRepository = invitationRepository;
    this.workspaceMemberRepository = workspaceMemberRepository;
    this.authorizationService = authorizationService;
    this.tokenService = tokenService;
    this.identityClient = identityClient;
    this.identityClientCircuitBreaker = identityClientCircuitBreaker;
    this.identityClientRetry = identityClientRetry;
    this.properties = properties;
    this.mapper = mapper;
    this.auditService = auditService;
    this.tenantDatabaseSession = tenantDatabaseSession;
    this.strictWorkspaceHeaderValidator = strictWorkspaceHeaderValidator;
  }

  @Transactional
  public InvitationResponse create(
      UserContext user, UUID workspaceId, CreateInvitationRequest request) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    var creator =
        authorizationService.requireWorkspaceRole(
            workspaceId, user.userId(), WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    if (creator.getRole() == WorkspaceRole.ADMIN && request.role() == WorkspaceRole.OWNER) {
      throw Exceptions.forbidden(
          "WORKSPACE_ACCESS_DENIED", "Admin cannot create owner invitations");
    }
    tryIdentityLookup(request.email());

    String plaintextToken = tokenService.generatePlaintextToken();
    String tokenHash = tokenService.hash(plaintextToken);
    Instant now = Instant.now();
    Invitation invitation =
        new Invitation(
            UUID.randomUUID(),
            workspaceId,
            normalizeEmail(request.email()),
            tokenHash,
            request.role(),
            now.plus(properties.invitations().ttlDays(), ChronoUnit.DAYS),
            user.userId(),
            now);
    invitationRepository.save(invitation);
    auditService.record(
        "INVITATION_CREATED",
        user.userId(),
        workspaceId,
        "INVITATION",
        invitation.getId(),
        Map.of("role", request.role().name()));

    String acceptUrl = acceptUrl(plaintextToken);
    log.info(
        "Invitation created workspaceId={} email={} acceptUrl={}",
        workspaceId,
        invitation.getEmail(),
        acceptUrl);
    return invitationResponse(invitation, plaintextToken, acceptUrl);
  }

  @Transactional(readOnly = true)
  public PageResponse<InvitationResponse> list(
      UserContext user, UUID workspaceId, Pageable pageable) {
    tenantDatabaseSession.applyWorkspace(workspaceId);
    authorizationService.requireWorkspaceRole(
        workspaceId, user.userId(), WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
    return PageResponse.from(
        invitationRepository.findByWorkspaceId(workspaceId, pageable).map(mapper::toResponse));
  }

  @Transactional
  public InvitationResponse accept(UserContext user, AcceptInvitationRequest request) {
    if (user.email() == null || user.email().isBlank()) {
      throw Exceptions.unauthorized(
          "MISSING_USER_EMAIL", "X-User-Email header is required to accept invitation");
    }
    Invitation invitation =
        invitationRepository
            .findByTokenHash(tokenService.hash(request.token()))
            .orElseThrow(
                () ->
                    Exceptions.badRequest("INVALID_INVITATION_TOKEN", "Invalid invitation token"));
    tenantDatabaseSession.applyWorkspace(invitation.getWorkspaceId());
    validateInvitation(invitation);
    if (!normalizeEmail(user.email()).equals(invitation.getEmail())) {
      throw Exceptions.forbidden(
          "INVITATION_EMAIL_MISMATCH", "Invitation email does not match authenticated user email");
    }

    Instant now = Instant.now();
    if (!workspaceMemberRepository.existsByIdWorkspaceIdAndIdUserId(
        invitation.getWorkspaceId(), user.userId())) {
      workspaceMemberRepository.save(
          new WorkspaceMember(
              invitation.getWorkspaceId(), user.userId(), invitation.getRole(), now));
    }
    invitation.accept(now);
    auditService.record(
        "INVITATION_ACCEPTED",
        user.userId(),
        invitation.getWorkspaceId(),
        "INVITATION",
        invitation.getId(),
        Map.of("role", invitation.getRole().name()));
    return mapper.toResponse(invitation);
  }

  @Transactional
  public InvitationResponse revoke(UserContext user, UUID invitationId) {
    strictWorkspaceHeaderValidator.requireWorkspaceHeader(user);
    Invitation invitation =
        invitationRepository
            .findById(invitationId)
            .orElseThrow(() -> Exceptions.notFound("INVITATION_NOT_FOUND", "Invitation not found"));
    tenantDatabaseSession.applyWorkspace(invitation.getWorkspaceId());
    strictWorkspaceHeaderValidator.validateAggregateRequest(user, invitation.getWorkspaceId());
    authorizationService.requireWorkspaceRole(
        invitation.getWorkspaceId(), user.userId(), WorkspaceRole.OWNER);
    invitation.revoke(Instant.now());
    auditService.record(
        "INVITATION_REVOKED",
        user.userId(),
        invitation.getWorkspaceId(),
        "INVITATION",
        invitation.getId(),
        Map.of());
    return mapper.toResponse(invitation);
  }

  private void validateInvitation(Invitation invitation) {
    if (invitation.getAcceptedAt() != null) {
      throw Exceptions.badRequest("INVITATION_ALREADY_ACCEPTED", "Invitation already accepted");
    }
    if (invitation.getRevokedAt() != null) {
      throw Exceptions.badRequest("INVITATION_REVOKED", "Invitation revoked");
    }
    if (invitation.getExpiresAt().isBefore(Instant.now())) {
      throw Exceptions.badRequest("INVITATION_EXPIRED", "Invitation expired");
    }
  }

  private InvitationResponse invitationResponse(
      Invitation invitation, String plaintextToken, String acceptUrl) {
    if (!properties.invitations().exposeTokenInResponse()) {
      plaintextToken = null;
      acceptUrl = null;
    }
    return new InvitationResponse(
        invitation.getId(),
        invitation.getWorkspaceId(),
        invitation.getEmail(),
        invitation.getRole(),
        invitation.getExpiresAt(),
        invitation.getAcceptedAt(),
        invitation.getRevokedAt(),
        invitation.getCreatedBy(),
        invitation.getCreatedAt(),
        plaintextToken,
        acceptUrl);
  }

  private String acceptUrl(String token) {
    String base = properties.invitations().acceptBaseUrl();
    String separator = base.contains("?") ? "&" : "?";
    return base + separator + "token=" + token;
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private void tryIdentityLookup(String email) {
    try {
      Retry.decorateSupplier(
              identityClientRetry,
              CircuitBreaker.decorateSupplier(
                  identityClientCircuitBreaker, () -> identityClient.findByEmail(email)))
          .get();
    } catch (Exception e) {
      log.debug(
          "Identity lookup unavailable for invitation email={}; continuing without lookup", email);
    }
  }
}
