package com.notebook.lumen.notification.shared.config;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
    String workerInstanceId,
    Email email,
    Internal internal,
    InApp inApp,
    Preferences preferences,
    Digest digest,
    Fanout fanout,
    WorkspaceClient workspace) {
  public record Email(
      String provider,
      String from,
      String replyTo,
      boolean workerEnabled,
      int maxAttempts,
      long retryInitialDelaySeconds,
      long retryMaxDelaySeconds,
      long workerFixedDelayMs,
      int workerBatchSize,
      long workerLockTimeoutSeconds,
      Smtp smtp,
      GenericHttp genericHttp,
      Webhooks webhooks) {}

  public record Smtp(String host, int port, String username, String password, boolean tlsEnabled) {}

  public record GenericHttp(
      String url,
      String apiKey,
      String authorizationHeader,
      long connectTimeoutMs,
      long requestTimeoutMs) {}

  public record Webhooks(
      boolean enabled,
      String provider,
      String secret,
      String signatureHeader,
      String timestampHeader,
      long toleranceSeconds,
      boolean requireTimestamp,
      boolean allowNoopVerifier) {}

  public record Internal(
      TrustedService trustedNotificationClient,
      TrustedService trustedIdentityClient,
      TrustedService trustedGatewayAdmin) {}

  public record InApp(boolean enabled) {}

  public record Preferences(boolean enabled) {}

  public record Digest(
      boolean enabled,
      boolean workerEnabled,
      long pollIntervalSeconds,
      int batchSize,
      int maxItemsPerEmail,
      String dailySendTime,
      DayOfWeek weeklyDay,
      String weeklySendTime) {}

  /**
   * Durable DB outbox for SSE fanout (Faz 64). Redis pub/sub remains the realtime fanout transport;
   * outbox ensures events survive process/Redis hiccups until published.
   */
  public record Fanout(
      boolean outboxEnabled,
      boolean workerEnabled,
      boolean immediateLocalDelivery,
      long pollIntervalSeconds,
      int batchSize,
      int maxAttempts,
      long backoffBaseSeconds,
      long backoffMaxSeconds,
      long lockTtlSeconds,
      long sentRetentionHours,
      long deadRetentionDays) {}

  /** Outbound calls from notification-service to workspace-service (Faz 65 / Faz 85). */
  public record WorkspaceClient(
      boolean preferencesEnabled,
      boolean policiesEnabled,
      boolean policyReasonRequiredForForce,
      String serviceUrl,
      long timeoutMs,
      OutboundServiceJwt serviceJwt) {}

  public record OutboundServiceJwt(
      String activeKid,
      String privateKey,
      String privateKeyPath,
      String issuer,
      String subject,
      String serviceName,
      long ttlSeconds,
      String audience) {
    public boolean signingConfigured() {
      return hasText(privateKey) || hasText(privateKeyPath);
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }

  public record TrustedService(
      String kid,
      String publicKey,
      String publicKeyPath,
      String issuer,
      String audience,
      long clockSkewSeconds,
      String allowedScopes) {
    public boolean configured() {
      return hasText(publicKey) || hasText(publicKeyPath);
    }

    public Duration clockSkew() {
      return Duration.ofSeconds(clockSkewSeconds <= 0 ? 5 : clockSkewSeconds);
    }

    public Set<String> allowedScopeSet() {
      if (!hasText(allowedScopes)) {
        return Set.of();
      }
      return Arrays.stream(allowedScopes.split(","))
          .map(String::trim)
          .filter(scope -> !scope.isBlank())
          .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasText(String value) {
      return value != null && !value.isBlank();
    }
  }
}
