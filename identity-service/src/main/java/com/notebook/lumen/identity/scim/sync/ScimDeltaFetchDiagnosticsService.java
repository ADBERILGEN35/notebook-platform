package com.notebook.lumen.identity.scim.sync;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimProviderResponseClassifier.Classification;
import com.notebook.lumen.identity.scim.sync.ScimRetryAfterParser.ParseResult;
import com.notebook.lumen.identity.scim.sync.ScimSyncDiagnosticsDtos.DryRunPocRequest;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderClient;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderRequest;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaProviderRequestBuilder;
import com.notebook.lumen.identity.scim.sync.delta.ScimDeltaRemoteFetchWarnings;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Evaluates simulated or read-only remote GET fetch outcomes for delta POC (Faz 116–117). Remote fetch is
 * manual dry-run only; default disabled; no IdP mutation.
 */
@Service
public class ScimDeltaFetchDiagnosticsService {

  private final ScimProperties properties;
  private final ScimDeltaProviderClient providerClient;
  private final ScimDeltaProviderRequestBuilder requestBuilder;

  public ScimDeltaFetchDiagnosticsService(
      ScimProperties properties,
      ScimDeltaProviderClient providerClient,
      ScimDeltaProviderRequestBuilder requestBuilder) {
    this.properties = properties;
    this.providerClient = providerClient;
    this.requestBuilder = requestBuilder;
  }

  public ScimDeltaRateLimitDiagnostics evaluateReadiness(List<String> baseWarnings) {
    Set<String> warnings = new LinkedHashSet<>(baseWarnings);
    appendRemoteFetchReadinessWarnings(warnings);
    return ScimDeltaRateLimitDiagnostics.empty(properties, List.copyOf(warnings));
  }

  public ScimDeltaRateLimitDiagnostics evaluateDryRun(DryRunPocRequest request, List<String> baseWarnings) {
    Set<String> warnings = new LinkedHashSet<>(baseWarnings);
    warnings.add(ScimDeltaStrategyResolver.WARNING_RAW_PAYLOAD_SUPPRESSED);

    if (!properties.deltaRemoteFetchEnabled()) {
      warnings.add(ScimProviderResponseClassifier.WARNING_REMOTE_FETCH_DISABLED);
    }

    if (hasSimulation(request)) {
      return evaluateSimulatedProbe(request, warnings);
    }

    if (!properties.deltaRemoteFetchEnabled()) {
      return evaluateSimulatedProbe(request, warnings);
    }

    if (!properties.deltaRemoteFetchRuntimeReady()) {
      warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_NOT_CONFIGURED);
      return ScimDeltaRateLimitDiagnostics.empty(properties, List.copyOf(warnings));
    }

    ScimResourceType resourceType =
        request == null || request.resourceType() == null ? ScimResourceType.USER : request.resourceType();
    return requestBuilder
        .buildDiagnosticPage(properties, resourceType)
        .map(req -> evaluateRemoteFetch(req, warnings))
        .orElseGet(
            () -> {
              warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_NOT_CONFIGURED);
              return ScimDeltaRateLimitDiagnostics.empty(properties, List.copyOf(warnings));
            });
  }

  private ScimDeltaRateLimitDiagnostics evaluateRemoteFetch(
      ScimDeltaProviderRequest providerRequest, Set<String> warnings) {
    if (providerRequest.pageSize() < properties.deltaRemoteMaxPageSize()
        || providerRequest.pageSize() < properties.providerMaxPageSize()) {
      warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_PAGE_SIZE_CAPPED);
    }
    var fetchResult =
        providerClient.fetch(providerRequest, properties.deltaRemoteBearerToken());
    return ScimDeltaRateLimitDiagnostics.fromRemoteFetch(
        properties, List.copyOf(warnings), fetchResult);
  }

  private ScimDeltaRateLimitDiagnostics evaluateSimulatedProbe(
      DryRunPocRequest request, Set<String> warnings) {
    Instant now = Instant.now();
    int backoffBase = Math.max(1, properties.deltaBackoffBaseSeconds());

    if (request != null && Boolean.TRUE.equals(request.simulatedTimeout())) {
      Classification c = ScimProviderResponseClassifier.classifyTimeout();
      warnings.addAll(c.warnings());
      return build(now, c, backoffBase, false, warnings);
    }

    if (request != null && Boolean.TRUE.equals(request.simulatedBadResponse())) {
      Classification c = ScimProviderResponseClassifier.classifyBadResponse();
      warnings.addAll(c.warnings());
      return build(now, c, backoffBase, false, warnings);
    }

    Integer status = request == null ? null : request.simulatedHttpStatus();
    if (status != null) {
      Classification c = ScimProviderResponseClassifier.classifyHttpStatus(status);
      warnings.addAll(c.warnings());
      int wait = backoffBase;
      boolean observed = false;
      boolean capped = false;

      if (c.retryable() && request.simulatedRetryAfter() != null) {
        ParseResult parsed =
            ScimRetryAfterParser.parse(
                request.simulatedRetryAfter(),
                properties.deltaMaxRetryAfterSeconds(),
                backoffBase);
        warnings.addAll(parsed.warnings());
        wait = parsed.retryAfterSeconds();
        observed = parsed.retryAfterObserved();
        capped = parsed.retryAfterCapped();
      } else if (c.errorClass() == ScimProviderErrorClass.RATE_LIMITED) {
        wait = backoffBase;
        warnings.add(ScimDeltaStrategyResolver.WARNING_RETRY_AFTER_OBSERVED);
      }

      return new ScimDeltaRateLimitDiagnostics(
          properties.deltaRemoteFetchEnabled(),
          properties.deltaRemoteFetchConfigured(),
          false,
          properties.providerRateLimitAware(),
          observed,
          observed ? wait : null,
          capped,
          ScimRetryAfterParser.nextRecommendedAttempt(now, wait),
          c.errorClass(),
          properties.deltaBackoffBaseSeconds(),
          properties.deltaHttpTimeoutMs(),
          0,
          0,
          false,
          List.copyOf(warnings));
    }

    if (request != null && request.observedRetryAfter()) {
      int seconds =
          request.retryAfterSeconds() == null ? backoffBase : request.retryAfterSeconds();
      ParseResult parsed =
          ScimRetryAfterParser.parse(
              String.valueOf(seconds), properties.deltaMaxRetryAfterSeconds(), backoffBase);
      warnings.addAll(parsed.warnings());
      warnings.add(ScimDeltaStrategyResolver.WARNING_RETRY_AFTER_OBSERVED);
      return new ScimDeltaRateLimitDiagnostics(
          properties.deltaRemoteFetchEnabled(),
          properties.deltaRemoteFetchConfigured(),
          false,
          properties.providerRateLimitAware(),
          parsed.retryAfterObserved(),
          parsed.retryAfterSeconds(),
          parsed.retryAfterCapped(),
          ScimRetryAfterParser.nextRecommendedAttempt(now, parsed.retryAfterSeconds()),
          ScimProviderErrorClass.RETRY_AFTER_OBSERVED,
          properties.deltaBackoffBaseSeconds(),
          properties.deltaHttpTimeoutMs(),
          0,
          0,
          false,
          List.copyOf(warnings));
    }

    if (!properties.deltaRemoteFetchEnabled()) {
      warnings.addAll(ScimProviderResponseClassifier.classifyRemoteFetchDisabled().warnings());
    }
    return build(
        now,
        new Classification(ScimProviderErrorClass.NONE, false, List.of()),
        backoffBase,
        false,
        warnings);
  }

  private ScimDeltaRateLimitDiagnostics build(
      Instant now,
      Classification classification,
      int waitSeconds,
      boolean capped,
      Set<String> warnings) {
    boolean observed =
        classification.errorClass() == ScimProviderErrorClass.RATE_LIMITED
            || classification.errorClass() == ScimProviderErrorClass.RETRY_AFTER_OBSERVED;
    if (classification.retryable() && classification.errorClass() != ScimProviderErrorClass.NONE) {
      warnings.add(ScimProviderResponseClassifier.WARNING_BACKOFF_RECOMMENDED);
    }
    return new ScimDeltaRateLimitDiagnostics(
        properties.deltaRemoteFetchEnabled(),
        properties.deltaRemoteFetchConfigured(),
        false,
        properties.providerRateLimitAware(),
        observed,
        observed || classification.retryable() ? waitSeconds : null,
        capped,
        classification.retryable() || observed
            ? ScimRetryAfterParser.nextRecommendedAttempt(now, waitSeconds)
            : null,
        classification.errorClass(),
        properties.deltaBackoffBaseSeconds(),
        properties.deltaHttpTimeoutMs(),
        0,
        0,
        false,
        List.copyOf(warnings));
  }

  private void appendRemoteFetchReadinessWarnings(Set<String> warnings) {
    if (!properties.deltaRemoteFetchEnabled()) {
      warnings.add(ScimProviderResponseClassifier.WARNING_REMOTE_FETCH_DISABLED);
    } else if (!properties.deltaRemoteFetchConfigured()) {
      warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_NOT_CONFIGURED);
    }
  }

  private static boolean hasSimulation(DryRunPocRequest request) {
    if (request == null) {
      return false;
    }
    return request.simulatedHttpStatus() != null
        || Boolean.TRUE.equals(request.simulatedTimeout())
        || Boolean.TRUE.equals(request.simulatedBadResponse())
        || request.observedRetryAfter();
  }

  public String runErrorCode(ScimDeltaRateLimitDiagnostics diagnostics) {
    if (diagnostics.providerErrorClass() == null
        || diagnostics.providerErrorClass() == ScimProviderErrorClass.NONE) {
      return null;
    }
    return diagnostics.providerErrorClass().name();
  }

  public String runErrorSummary(ScimDeltaRateLimitDiagnostics diagnostics) {
    return ScimDeltaRunMetadataCodec.encode(diagnostics);
  }
}
