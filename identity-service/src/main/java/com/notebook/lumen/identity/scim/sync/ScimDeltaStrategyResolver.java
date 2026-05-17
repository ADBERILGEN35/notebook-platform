package com.notebook.lumen.identity.scim.sync;

import com.notebook.lumen.identity.scim.ScimProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves provider-specific delta strategy and safe diagnostics (Faz 115). */
@Component
public class ScimDeltaStrategyResolver {

  static final String WARNING_PROVIDER_UNSUPPORTED = "SCIM_DELTA_PROVIDER_UNSUPPORTED";
  static final String WARNING_DRY_RUN_ONLY = "SCIM_DELTA_DRY_RUN_ONLY";
  static final String WARNING_FILTERING_UNAVAILABLE = "SCIM_DELTA_FILTERING_UNAVAILABLE";
  static final String WARNING_RETRY_AFTER_OBSERVED = "SCIM_DELTA_RETRY_AFTER_OBSERVED";
  static final String WARNING_CHECKPOINT_STALE = "SCIM_DELTA_CHECKPOINT_STALE";
  static final String WARNING_MISSING_USER_IGNORED = "SCIM_DELTA_MISSING_USER_IGNORED";
  static final String WARNING_RATE_LIMITED = "SCIM_DELTA_RATE_LIMITED";
  static final String WARNING_RAW_PAYLOAD_SUPPRESSED = "SCIM_DELTA_RAW_PAYLOAD_SUPPRESSED";

  private static final Duration CHECKPOINT_STALE_THRESHOLD = Duration.ofDays(7);

  public ScimDeltaStrategyPlan resolve(ScimProperties properties) {
    ScimDeltaProviderKind kind = ScimDeltaProviderKind.fromConfig(properties.providerType());
    List<String> warnings = new ArrayList<>();
    warnings.add(WARNING_MISSING_USER_IGNORED);
    warnings.add(WARNING_RAW_PAYLOAD_SUPPRESSED);

    if (!properties.deltaProviderPocEnabled()) {
      warnings.add(WARNING_PROVIDER_UNSUPPORTED);
      return plan(
          kind,
          properties,
          ScimDeltaSyncStrategy.DISABLED,
          "delta-poc-disabled",
          false,
          true,
          warnings);
    }

    if (properties.deltaDryRunOnly()) {
      warnings.add(WARNING_DRY_RUN_ONLY);
    }

    if (!properties.providerRateLimitAware()) {
      warnings.add(WARNING_RATE_LIMITED);
    }

    ScimDeltaSyncStrategy strategy = selectStrategy(kind, properties, warnings);
    String deltaSource = describeDeltaSource(kind, strategy);
    boolean pagination = supportsPagination(kind, properties);
    boolean capabilityAligned = capabilityAligned(strategy, properties);
    if (!capabilityAligned) {
      warnings.add(WARNING_FILTERING_UNAVAILABLE);
    }
    return plan(kind, properties, strategy, deltaSource, pagination, capabilityAligned, warnings);
  }

  public List<String> warningsForCheckpoint(Instant lastSuccessfulSyncAt) {
    List<String> out = new ArrayList<>();
    if (lastSuccessfulSyncAt != null
        && lastSuccessfulSyncAt.isBefore(Instant.now().minus(CHECKPOINT_STALE_THRESHOLD))) {
      out.add(WARNING_CHECKPOINT_STALE);
    }
    return out;
  }

  public String deprovisionSemantics(ScimDeltaProviderKind kind) {
    return switch (kind) {
      case OKTA ->
          "Deprovision only on explicit active=false or DELETE from Okta; users missing from a delta"
              + " page are not deprovisioned.";
      case AZURE_AD ->
          "Entra provisioning: missing from pagination/delta is not deletion; deprovision only"
              + " explicit active=false or DELETE.";
      case GENERIC ->
          "Conservative: missing-from-delta never deprovisions; active=false and DELETE are soft"
              + " deprovision locally.";
    };
  }

  private ScimDeltaSyncStrategy selectStrategy(
      ScimDeltaProviderKind kind, ScimProperties properties, List<String> warnings) {
    return switch (kind) {
      case OKTA -> {
        if (!properties.providerSupportsFiltering()) {
          warnings.add(WARNING_FILTERING_UNAVAILABLE);
          yield ScimDeltaSyncStrategy.FULL_SYNC_FALLBACK;
        }
        yield ScimDeltaSyncStrategy.LAST_MODIFIED_FILTER;
      }
      case AZURE_AD -> {
        if (!properties.providerSupportsFiltering()) {
          warnings.add(WARNING_FILTERING_UNAVAILABLE);
          yield ScimDeltaSyncStrategy.FULL_SYNC_FALLBACK;
        }
        yield ScimDeltaSyncStrategy.CURSOR_CHECKPOINT;
      }
      case GENERIC -> {
        if (properties.deltaSyncEnabled() && properties.providerSupportsFiltering()) {
          yield ScimDeltaSyncStrategy.FULL_SYNC_FALLBACK;
        }
        warnings.add(WARNING_PROVIDER_UNSUPPORTED);
        yield ScimDeltaSyncStrategy.DISABLED;
      }
    };
  }

  private static boolean supportsPagination(ScimDeltaProviderKind kind, ScimProperties properties) {
    return switch (kind) {
      case OKTA, AZURE_AD -> true;
      case GENERIC -> properties.providerSupportsFiltering();
    };
  }

  private static String describeDeltaSource(
      ScimDeltaProviderKind kind, ScimDeltaSyncStrategy strategy) {
    return switch (strategy) {
          case DISABLED -> "disabled";
          case LAST_MODIFIED_FILTER -> "lastModified-filter-poc";
          case CURSOR_CHECKPOINT -> "cursor-checkpoint-poc";
          case FULL_SYNC_FALLBACK -> "full-sync-fallback-poc";
        }
        + ":"
        + kind.configValue();
  }

  private static boolean capabilityAligned(
      ScimDeltaSyncStrategy strategy, ScimProperties properties) {
    return switch (strategy) {
      case DISABLED, FULL_SYNC_FALLBACK -> true;
      case LAST_MODIFIED_FILTER, CURSOR_CHECKPOINT -> properties.providerSupportsFiltering();
    };
  }

  private ScimDeltaStrategyPlan plan(
      ScimDeltaProviderKind kind,
      ScimProperties properties,
      ScimDeltaSyncStrategy strategy,
      String deltaSource,
      boolean supportsPagination,
      boolean capabilityAligned,
      List<String> warnings) {
    return new ScimDeltaStrategyPlan(
        kind.configValue(),
        properties.deltaProviderPocEnabled(),
        properties.deltaDryRunOnly(),
        strategy,
        deltaSource,
        properties.providerSupportsFiltering(),
        supportsPagination,
        properties.providerSupportsPatch(),
        properties.providerRateLimitAware(),
        capabilityAligned,
        deprovisionSemantics(kind),
        List.copyOf(warnings));
  }

  public record ScimDeltaStrategyPlan(
      String providerType,
      boolean deltaPocEnabled,
      boolean dryRunOnly,
      ScimDeltaSyncStrategy selectedStrategy,
      String deltaSource,
      boolean supportsFiltering,
      boolean supportsPagination,
      boolean supportsPatch,
      boolean supportsRetryAfter,
      boolean capabilityAligned,
      String deprovisionSemantics,
      List<String> warnings) {}
}
