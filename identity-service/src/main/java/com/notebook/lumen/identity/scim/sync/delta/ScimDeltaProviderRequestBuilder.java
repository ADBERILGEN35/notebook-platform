package com.notebook.lumen.identity.scim.sync.delta;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimDeltaProviderKind;
import com.notebook.lumen.identity.scim.sync.ScimResourceType;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Builds read-only GET URIs per provider strategy (Faz 117). */
@Component
public class ScimDeltaProviderRequestBuilder {

  public Optional<ScimDeltaProviderRequest> buildDiagnosticPage(
      ScimProperties properties, ScimResourceType resourceType) {
    if (!properties.deltaRemoteFetchRuntimeReady()) {
      return Optional.empty();
    }
    ScimDeltaProviderKind kind = ScimDeltaProviderKind.fromConfig(properties.providerType());
    int pageSize = cappedPageSize(properties);
    String base = normalizeBaseUrl(properties.deltaRemoteBaseUrl());
    String path = resourceType == ScimResourceType.GROUP ? "/Groups" : "/Users";

    return switch (kind) {
      case OKTA -> Optional.of(oktaRequest(base, path, pageSize, resourceType));
      case AZURE_AD -> Optional.of(entraRequest(base, path, pageSize, resourceType));
      case GENERIC -> Optional.of(genericRequest(base, path, pageSize, resourceType));
    };
  }

  private static ScimDeltaProviderRequest oktaRequest(
      String base, String path, int pageSize, ScimResourceType resourceType) {
    String since = Instant.now().minusSeconds(86400).toString();
    String filter = "meta.lastModified gt \"" + since + "\"";
    String uri =
        base
            + path
            + "?filter="
            + URLEncoder.encode(filter, StandardCharsets.UTF_8)
            + "&count="
            + pageSize
            + "&startIndex=1";
    return new ScimDeltaProviderRequest(ScimDeltaHttpMethod.GET, uri, resourceType, pageSize);
  }

  private static ScimDeltaProviderRequest entraRequest(
      String base, String path, int pageSize, ScimResourceType resourceType) {
    String uri = base + path + "?$top=" + pageSize;
    return new ScimDeltaProviderRequest(ScimDeltaHttpMethod.GET, uri, resourceType, pageSize);
  }

  private static ScimDeltaProviderRequest genericRequest(
      String base, String path, int pageSize, ScimResourceType resourceType) {
    String uri = base + path + "?count=" + pageSize + "&startIndex=1";
    return new ScimDeltaProviderRequest(ScimDeltaHttpMethod.GET, uri, resourceType, pageSize);
  }

  private static int cappedPageSize(ScimProperties properties) {
    int configured = Math.max(1, properties.deltaRemoteMaxPageSize());
    int providerMax = Math.max(1, properties.providerMaxPageSize());
    return Math.min(configured, providerMax);
  }

  private static String normalizeBaseUrl(String baseUrl) {
    String trimmed = baseUrl.trim();
    if (trimmed.endsWith("/")) {
      return trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }
}
