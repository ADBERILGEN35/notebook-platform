package com.notebook.lumen.workspace.api;

import com.notebook.lumen.workspace.dto.InvitationResponse;
import com.notebook.lumen.workspace.dto.Requests.*;
import com.notebook.lumen.workspace.service.InvitationService;
import com.notebook.lumen.workspace.shared.UserContext;
import com.notebook.lumen.workspace.shared.UserContextResolver;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class InvitationController {
  private final InvitationService invitationService;
  private final UserContextResolver userContextResolver;

  public InvitationController(
      InvitationService invitationService, UserContextResolver userContextResolver) {
    this.invitationService = invitationService;
    this.userContextResolver = userContextResolver;
  }

  @PostMapping("/workspaces/{workspaceId}/invitations")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create invitation")
  public InvitationResponse create(
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateInvitationRequest request,
      HttpServletRequest httpRequest) {
    return invitationService.create(user(httpRequest), workspaceId, request);
  }

  @GetMapping("/workspaces/{workspaceId}/invitations")
  @Operation(summary = "List invitations")
  public List<InvitationResponse> list(
      @PathVariable UUID workspaceId, HttpServletRequest httpRequest) {
    return invitationService.list(user(httpRequest), workspaceId);
  }

  @PostMapping("/invitations/accept")
  @Operation(summary = "Accept invitation")
  public InvitationResponse accept(
      @Valid @RequestBody AcceptInvitationRequest request, HttpServletRequest httpRequest) {
    return invitationService.accept(user(httpRequest), request);
  }

  @PostMapping("/invitations/{invitationId}/revoke")
  @Operation(summary = "Revoke invitation")
  public InvitationResponse revoke(
      @PathVariable UUID invitationId, HttpServletRequest httpRequest) {
    return invitationService.revoke(user(httpRequest), invitationId);
  }

  private UserContext user(HttpServletRequest request) {
    return userContextResolver.require(request);
  }
}
