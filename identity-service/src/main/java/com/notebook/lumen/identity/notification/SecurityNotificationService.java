package com.notebook.lumen.identity.notification;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SecurityNotificationService {
  private static final Logger log = LoggerFactory.getLogger(SecurityNotificationService.class);

  private final IdentityNotificationProperties properties;
  private final NotificationClient notificationClient;
  private final AuditService auditService;

  public SecurityNotificationService(
      IdentityNotificationProperties properties,
      NotificationClient notificationClient,
      AuditService auditService) {
    this.properties = properties;
    this.notificationClient = notificationClient;
    this.auditService = auditService;
  }

  public void refreshTokensRevoked(User user, int revokedCount, HttpServletRequest request) {
    if (!properties.enabled() || user == null || user.getEmail() == null || user.getEmail().isBlank()) {
      return;
    }
    try {
      notificationClient.sendEmail(
          new NotificationClient.NotificationEmailRequest(
              NotificationClient.EmailNotificationType.SECURITY_REFRESH_TOKENS_REVOKED,
              user.getEmail(),
              "Security notice: sessions revoked",
              "security-refresh-tokens-revoked",
              Map.of("revokedAt", Instant.now().toString()),
              "security-revoke-all:" + user.getId() + ":" + Instant.now().getEpochSecond() / 60,
              user.getId()));
      notificationClient.sendInApp(
          new NotificationClient.InAppNotificationRequest(
              user.getId(),
              null,
              NotificationClient.InAppNotificationType.SECURITY_SESSIONS_REVOKED,
              "Security notice",
              "All active sessions were revoked. Re-login required on other devices.",
              "WARNING",
              "/app/settings/security",
              Map.of("source", "identity-revoke-all", "revokedCount", revokedCount),
              "security-revoke-all:in-app:"
                  + user.getId()
                  + ":"
                  + Instant.now().getEpochSecond() / 60));
      auditService.record(
          "SECURITY_NOTIFICATION_REQUESTED",
          user.getId(),
          "USER",
          user.getId(),
          request,
          Map.of("type", "SECURITY_REFRESH_TOKENS_REVOKED", "revokedCount", revokedCount));
    } catch (RuntimeException e) {
      log.warn(
          "Security notification request failed userId={} errorClass={}",
          user.getId(),
          e.getClass().getSimpleName());
      auditService.record(
          "SECURITY_NOTIFICATION_FAILED",
          user.getId(),
          "USER",
          user.getId(),
          request,
          Map.of("type", "SECURITY_REFRESH_TOKENS_REVOKED", "error", e.getClass().getSimpleName()));
    }
  }
}
