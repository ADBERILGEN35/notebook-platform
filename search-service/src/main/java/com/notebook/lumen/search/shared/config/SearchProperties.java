package com.notebook.lumen.search.shared.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public record SearchProperties(
    int maxIndexedChars,
    int maxQueryLength,
    int minQueryLength,
    int maxPageSize,
    String workerInstanceId,
    String provider,
    boolean dualWriteEnabled,
    boolean fallbackToPostgres,
    boolean permissionSnapshotEnabled,
    boolean permissionRuntimeCheckEnabled,
    boolean permissionRefreshEnabled,
    OpenSearch opensearch,
    Workspace workspace,
    ContentSource contentSource,
    ServiceJwt serviceJwt,
    Internal internal,
    Reindex reindex) {
  public SearchProperties(
      int maxIndexedChars,
      int maxQueryLength,
      int minQueryLength,
      int maxPageSize,
      String workerInstanceId,
      String provider,
      boolean dualWriteEnabled,
      boolean fallbackToPostgres,
      OpenSearch opensearch,
      Workspace workspace,
      ContentSource contentSource,
      ServiceJwt serviceJwt,
      Internal internal,
      Reindex reindex) {
    this(
        maxIndexedChars,
        maxQueryLength,
        minQueryLength,
        maxPageSize,
        workerInstanceId,
        provider,
        dualWriteEnabled,
        fallbackToPostgres,
        true,
        true,
        true,
        opensearch,
        workspace,
        contentSource,
        serviceJwt,
        internal,
        reindex);
  }
  public record Workspace(String serviceUrl, long timeoutMs, int retryMaxAttempts) {}

  public record ContentSource(String serviceUrl, long timeoutMs, String audience) {}

  public record OpenSearch(
      String url,
      String username,
      String password,
      String indexNotes,
      long connectTimeoutMs,
      long socketTimeoutMs,
      boolean tlsEnabled,
      String truststorePath) {
    public String effectiveIndexNotes() {
      return hasText(indexNotes) ? indexNotes : "notebook-notes";
    }

    public long effectiveConnectTimeoutMs() {
      return connectTimeoutMs <= 0 ? 1000 : connectTimeoutMs;
    }

    public long effectiveSocketTimeoutMs() {
      return socketTimeoutMs <= 0 ? 3000 : socketTimeoutMs;
    }

    public boolean configured() {
      return hasText(url);
    }
  }

  public record ServiceJwt(
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
  }

  public record Internal(
      TrustedService trustedIndexingClient,
      TrustedService trustedReindexClient,
      TrustedService trustedWorkspaceClient) {
    public Internal(TrustedService trustedIndexingClient, TrustedService trustedReindexClient) {
      this(trustedIndexingClient, trustedReindexClient, null);
    }
  }

  public record Reindex(
      boolean workerEnabled,
      int batchSize,
      int pollIntervalSeconds,
      int maxFailures,
      boolean orphanCleanupEnabled,
      long lockTimeoutSeconds,
      long heartbeatIntervalSeconds) {
    public int effectiveBatchSize() {
      return batchSize <= 0 ? 100 : Math.min(batchSize, 500);
    }

    public int effectivePollIntervalSeconds() {
      return pollIntervalSeconds <= 0 ? 10 : pollIntervalSeconds;
    }

    public int effectiveMaxFailures() {
      return maxFailures <= 0 ? 100 : maxFailures;
    }

    public long effectiveLockTimeoutSeconds() {
      return lockTimeoutSeconds <= 0 ? 300 : lockTimeoutSeconds;
    }

    public long effectiveHeartbeatIntervalSeconds() {
      return heartbeatIntervalSeconds <= 0 ? 30 : heartbeatIntervalSeconds;
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
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
