package com.notebook.lumen.identity.siem.application;

import com.notebook.lumen.identity.siem.SiemProperties;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SiemEventClassifier {
  private final SiemProperties properties;

  public SiemEventClassifier(SiemProperties properties) {
    this.properties = properties;
  }

  public Optional<ClassifiedEvent> classify(String eventType) {
    if (eventType == null || eventType.isBlank()) {
      return Optional.empty();
    }
    String v = eventType.toUpperCase(Locale.ROOT);
    if ("USER_LOGIN_SUCCEEDED".equals(v) && !properties.includeLoginSuccess()) {
      return Optional.empty();
    }
    return switch (v) {
      case "USER_LOGIN_FAILED" -> Optional.of(new ClassifiedEvent("AUTH_SECURITY", "HIGH"));
      case "USER_LOGIN_SUCCEEDED" -> Optional.of(new ClassifiedEvent("AUTH_SECURITY", "INFO"));
      case "REFRESH_TOKEN_REVOKED", "REFRESH_TOKENS_REVOKED_ALL" ->
          Optional.of(new ClassifiedEvent("TOKEN_REVOCATION", "HIGH"));
      case "MFA_ENABLED", "MFA_RECOVERY_CODE_USED", "MFA_CREDENTIAL_REVOKED" ->
          Optional.of(new ClassifiedEvent("MFA", "HIGH"));
      case "SCIM_USER_DEPROVISIONED", "SCIM_AUTH_FAILED" ->
          Optional.of(new ClassifiedEvent("SCIM", "HIGH"));
      case "USER_REFRESH_TOKENS_REVOKED_BY_SCIM" ->
          Optional.of(new ClassifiedEvent("SCIM", "HIGH"));
      case "SSO_LOGIN_SUCCESS",
          "SSO_LOGIN_FAILED",
          "SSO_DISABLED",
          "SSO_STATE_INVALID",
          "SSO_STATE_EXPIRED" ->
          Optional.of(new ClassifiedEvent("SSO", "MEDIUM"));
      default -> Optional.empty();
    };
  }

  public record ClassifiedEvent(String category, String severity) {}
}
