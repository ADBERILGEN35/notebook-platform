package com.notebook.lumen.notification.workspace;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.notification.policy.application.WorkspaceRoleRules;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WorkspaceMembershipClient {
  public static final String PERMISSION_READ_SCOPE = "internal:workspace:permission:read";

  private final NotificationProperties properties;
  private final RestClient restClient;
  private final ServiceJwtSigner signer;

  public WorkspaceMembershipClient(NotificationProperties properties) {
    this.properties = properties;
    var w = properties.workspace();
    String base = w.serviceUrl() == null ? "" : w.serviceUrl().trim();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.restClient =
        RestClient.builder().baseUrl(base.isEmpty() ? "http://localhost:8082" : base).build();
    this.signer = buildSigner(w);
  }

  private static ServiceJwtSigner buildSigner(NotificationProperties.WorkspaceClient w) {
    if ((!w.preferencesEnabled() && !w.policiesEnabled())
        || w.serviceJwt() == null
        || !w.serviceJwt().signingConfigured()) {
      return null;
    }
    var jwt = w.serviceJwt();
    return new ServiceJwtSigner(
        new ServiceJwtProperties(
            jwt.activeKid(),
            jwt.privateKey(),
            jwt.privateKeyPath(),
            jwt.issuer(),
            jwt.subject(),
            jwt.serviceName(),
            Duration.ofSeconds(jwt.ttlSeconds() <= 0 ? 60 : jwt.ttlSeconds())));
  }

  public void requireWorkspaceMember(UUID userId, UUID workspaceId) {
    var w = properties.workspace();
    if (!w.preferencesEnabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND,
          "WORKSPACE_NOTIFICATION_PREFERENCES_DISABLED",
          "Workspace notification preferences are disabled");
    }
    WorkspaceMembershipPayload payload =
        fetchMembership(userId, workspaceId, "WORKSPACE_NOTIFICATION_PREFERENCE_ACCESS_DENIED");
    if (!payload.isMember()) {
      throw new NotificationException(
          HttpStatus.FORBIDDEN,
          "WORKSPACE_NOTIFICATION_PREFERENCE_ACCESS_DENIED",
          "User is not a member of this workspace");
    }
  }

  public WorkspaceMembershipPayload requireWorkspaceMemberForPoliciesRead(
      UUID userId, UUID workspaceId) {
    var w = properties.workspace();
    if (!w.policiesEnabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND,
          "WORKSPACE_NOTIFICATION_POLICIES_DISABLED",
          "Workspace notification policies are disabled");
    }
    WorkspaceMembershipPayload payload =
        fetchMembership(userId, workspaceId, "WORKSPACE_NOTIFICATION_POLICY_ACCESS_DENIED");
    if (!payload.isMember()) {
      throw new NotificationException(
          HttpStatus.FORBIDDEN,
          "WORKSPACE_NOTIFICATION_POLICY_ACCESS_DENIED",
          "User is not a member of this workspace");
    }
    return payload;
  }

  public WorkspaceMembershipPayload requireWorkspaceOwnerOrAdmin(UUID userId, UUID workspaceId) {
    var w = properties.workspace();
    if (!w.policiesEnabled()) {
      throw new NotificationException(
          HttpStatus.NOT_FOUND,
          "WORKSPACE_NOTIFICATION_POLICIES_DISABLED",
          "Workspace notification policies are disabled");
    }
    WorkspaceMembershipPayload payload =
        fetchMembership(userId, workspaceId, "WORKSPACE_NOTIFICATION_POLICY_ACCESS_DENIED");
    if (!payload.isMember() || !WorkspaceRoleRules.isOwnerOrAdmin(payload.role())) {
      throw new NotificationException(
          HttpStatus.FORBIDDEN,
          "WORKSPACE_NOTIFICATION_POLICY_ACCESS_DENIED",
          "Only workspace owners and admins can manage notification policies");
    }
    return payload;
  }

  private WorkspaceMembershipPayload fetchMembership(
      UUID userId, UUID workspaceId, String accessDeniedCode) {
    if (signer == null) {
      throw new NotificationException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "WORKSPACE_SERVICE_UNAVAILABLE",
          "Workspace membership client is not configured");
    }
    var w = properties.workspace();
    String token = signer.sign(w.serviceJwt().audience(), PERMISSION_READ_SCOPE);
    try {
      WorkspaceMembershipPayload payload =
          restClient
              .get()
              .uri(
                  "/internal/workspaces/{workspaceId}/permissions?userId={userId}",
                  workspaceId,
                  userId)
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
              .accept(MediaType.APPLICATION_JSON)
              .retrieve()
              .body(WorkspaceMembershipPayload.class);
      if (payload == null) {
        throw new NotificationException(
            HttpStatus.FORBIDDEN, accessDeniedCode, "Workspace access denied");
      }
      return payload;
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 403) {
        throw new NotificationException(
            HttpStatus.FORBIDDEN, accessDeniedCode, "Workspace access denied");
      }
      throw new NotificationException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "WORKSPACE_SERVICE_UNAVAILABLE",
          "Workspace service error");
    } catch (RestClientException ex) {
      throw new NotificationException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "WORKSPACE_SERVICE_UNAVAILABLE",
          "Workspace service unavailable");
    }
  }

  public record WorkspaceMembershipPayload(
      UUID workspaceId, UUID userId, boolean isMember, String role) {}
}
