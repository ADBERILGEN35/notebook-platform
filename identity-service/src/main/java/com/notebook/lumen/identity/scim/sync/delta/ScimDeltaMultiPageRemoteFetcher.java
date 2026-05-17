package com.notebook.lumen.identity.scim.sync.delta;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimDeltaProviderKind;
import com.notebook.lumen.identity.scim.sync.ScimDeltaRateLimitDiagnostics;
import com.notebook.lumen.identity.scim.sync.ScimProviderErrorClass;
import com.notebook.lumen.identity.scim.sync.ScimResourceType;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Bounded read-only GET page loop for manual dry-run (Faz 119). Never logs raw cursor, next URL, or
 * response body.
 */
@Component
public class ScimDeltaMultiPageRemoteFetcher {

  private final ScimProperties properties;
  private final ScimDeltaProviderClient providerClient;
  private final ScimDeltaProviderRequestBuilder requestBuilder;

  public ScimDeltaMultiPageRemoteFetcher(
      ScimProperties properties,
      ScimDeltaProviderClient providerClient,
      ScimDeltaProviderRequestBuilder requestBuilder) {
    this.properties = properties;
    this.providerClient = providerClient;
    this.requestBuilder = requestBuilder;
  }

  public ScimDeltaRateLimitDiagnostics fetchPages(
      ScimResourceType resourceType, String bearerToken, List<String> baseWarnings) {
    Set<String> warnings = new LinkedHashSet<>(baseWarnings);
    int maxPages = effectiveMaxPages();
    int maxResources = Math.max(1, properties.deltaRemoteMaxResources());

    if (!properties.deltaRemoteMultiPageEnabled()) {
      warnings.add(ScimDeltaRemoteFetchWarnings.MULTI_PAGE_DISABLED);
    } else {
      warnings.add(ScimDeltaRemoteFetchWarnings.MULTI_PAGE_ATTEMPTED);
    }

    Optional<ScimDeltaProviderRequest> firstRequest =
        requestBuilder.buildDiagnosticPage(properties, resourceType);
    if (firstRequest.isEmpty()) {
      warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_NOT_CONFIGURED);
      return ScimDeltaRateLimitDiagnostics.empty(
          properties, List.copyOf(warnings), ScimDeltaStoppedReason.NOT_CONFIGURED);
    }

    ScimDeltaProviderKind kind = ScimDeltaProviderKind.fromConfig(properties.providerType());
    String resourcePath = resourceType == ScimResourceType.GROUP ? "/Groups" : "/Users";
    String baseUrl = properties.deltaRemoteBaseUrl().trim();

    int pagesObserved = 0;
    int totalResources = 0;
    boolean nextCursorOnLastPage = false;
    ScimDeltaProviderFetchResult lastPage = null;
    ScimDeltaStoppedReason stoppedReason = ScimDeltaStoppedReason.SINGLE_PAGE_ONLY;
    boolean pageLimitReached = false;
    boolean resourceLimitReached = false;

    Optional<ScimDeltaPaginationContinuation> continuation = Optional.empty();
    ScimDeltaProviderRequest nextRequest = firstRequest.get();

    while (pagesObserved < maxPages && totalResources < maxResources) {
      if (pagesObserved > 0) {
        applyPageDelay();
      }

      ScimDeltaProviderFetchResult pageResult = providerClient.fetch(nextRequest, bearerToken);
      pagesObserved++;
      lastPage = pageResult;
      warnings.addAll(pageResult.warnings());

      if (pageResult.providerErrorClass() == ScimProviderErrorClass.RATE_LIMITED) {
        stoppedReason = ScimDeltaStoppedReason.PROVIDER_RATE_LIMITED;
        warnings.add(ScimDeltaRemoteFetchWarnings.RATE_LIMIT_STOPPED);
        warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_LOOP_STOPPED);
        break;
      }

      if (pageResult.providerErrorClass() == ScimProviderErrorClass.TIMEOUT) {
        stoppedReason = ScimDeltaStoppedReason.TIMEOUT;
        warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_LOOP_STOPPED);
        break;
      }

      if (pageResult.providerErrorClass() == ScimProviderErrorClass.PROVIDER_BAD_RESPONSE) {
        stoppedReason = ScimDeltaStoppedReason.BAD_RESPONSE;
        warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_LOOP_STOPPED);
        break;
      }

      if (pageResult.providerErrorClass() != ScimProviderErrorClass.NONE) {
        stoppedReason = ScimDeltaStoppedReason.PROVIDER_ERROR;
        warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_LOOP_STOPPED);
        break;
      }

      totalResources += pageResult.fetchedResourceCount();
      nextCursorOnLastPage = pageResult.nextCursorPresent();
      continuation = pageResult.paginationContinuation();

      if (!properties.deltaRemoteMultiPageEnabled()) {
        stoppedReason = ScimDeltaStoppedReason.SINGLE_PAGE_ONLY;
        break;
      }

      if (totalResources >= maxResources) {
        resourceLimitReached = true;
        stoppedReason = ScimDeltaStoppedReason.RESOURCE_LIMIT_REACHED;
        warnings.add(ScimDeltaRemoteFetchWarnings.RESOURCE_LIMIT_REACHED);
        warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_LOOP_STOPPED);
        break;
      }

      if (pagesObserved >= maxPages) {
        pageLimitReached = true;
        stoppedReason = ScimDeltaStoppedReason.PAGE_LIMIT_REACHED;
        warnings.add(ScimDeltaRemoteFetchWarnings.PAGE_LIMIT_REACHED);
        warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_LOOP_STOPPED);
        break;
      }

      if (continuation.isEmpty()) {
        stoppedReason = ScimDeltaStoppedReason.NO_NEXT_CURSOR;
        warnings.add(ScimDeltaRemoteFetchWarnings.REMOTE_LOOP_STOPPED);
        break;
      }

      String uri =
          continuation.get().buildRequestUri(baseUrl, resourcePath, nextRequest.pageSize(), kind);
      nextRequest =
          new ScimDeltaProviderRequest(
              ScimDeltaHttpMethod.GET, uri, resourceType, nextRequest.pageSize());
    }

    if (lastPage == null) {
      return ScimDeltaRateLimitDiagnostics.empty(
          properties, List.copyOf(warnings), ScimDeltaStoppedReason.NOT_CONFIGURED);
    }

    if (lastPage.providerErrorClass() == ScimProviderErrorClass.NONE
        && stoppedReason == ScimDeltaStoppedReason.SINGLE_PAGE_ONLY
        && properties.deltaRemoteMultiPageEnabled()
        && !pageLimitReached
        && !resourceLimitReached) {
      stoppedReason =
          nextCursorOnLastPage
              ? ScimDeltaStoppedReason.COMPLETED
              : ScimDeltaStoppedReason.NO_NEXT_CURSOR;
    }

    return ScimDeltaRateLimitDiagnostics.fromMultiPage(
        properties,
        List.copyOf(warnings),
        lastPage,
        pagesObserved,
        totalResources,
        nextCursorOnLastPage,
        stoppedReason,
        pageLimitReached,
        resourceLimitReached);
  }

  private int effectiveMaxPages() {
    int configured = Math.max(1, properties.deltaRemoteMaxPages());
    if (!properties.deltaRemoteMultiPageEnabled()) {
      return 1;
    }
    return configured;
  }

  private void applyPageDelay() {
    int delayMs = Math.max(0, properties.deltaRemotePageDelayMs());
    if (delayMs == 0) {
      return;
    }
    try {
      Thread.sleep(delayMs);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
