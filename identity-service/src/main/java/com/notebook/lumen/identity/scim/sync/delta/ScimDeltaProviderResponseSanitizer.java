package com.notebook.lumen.identity.scim.sync.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.identity.scim.sync.ScimDeltaProviderKind;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Extracts aggregate SCIM list diagnostics without retaining PII (Faz 117–119). */
public final class ScimDeltaProviderResponseSanitizer {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern FORBIDDEN_PAYLOAD_SNIPPETS =
      Pattern.compile(
          "(?i)(userName|emails?|displayName|name\\.formatted|password|Bearer\\s|Authorization)",
          Pattern.MULTILINE);

  private ScimDeltaProviderResponseSanitizer() {}

  public record SanitizedPage(
      int fetchedResourceCount,
      boolean validListResponseShape,
      boolean nextCursorPresent,
      Optional<ScimDeltaPaginationContinuation> continuation,
      List<String> warnings) {}

  public static SanitizedPage sanitize(String rawBody, ScimDeltaProviderKind providerKind) {
    List<String> warnings = new ArrayList<>();
    warnings.add(ScimDeltaRemoteFetchWarnings.PROVIDER_RESPONSE_SANITIZED);
    warnings.add("SCIM_DELTA_RAW_PAYLOAD_SUPPRESSED");

    if (rawBody == null || rawBody.isBlank()) {
      return new SanitizedPage(0, false, false, Optional.empty(), warnings);
    }

    if (FORBIDDEN_PAYLOAD_SNIPPETS.matcher(rawBody).find()) {
      return new SanitizedPage(0, false, false, Optional.empty(), warnings);
    }

    try {
      JsonNode root = MAPPER.readTree(rawBody);
      int count = extractCount(root);
      boolean validShape = root.has("schemas") || root.has("totalResults") || root.has("Resources");
      Optional<ScimDeltaPaginationContinuation> continuation =
          extractContinuation(root, providerKind, warnings);
      boolean nextCursor = continuation.isPresent();
      if (nextCursor) {
        warnings.add(ScimDeltaRemoteFetchWarnings.NEXT_CURSOR_PRESENT);
        warnings.add(ScimDeltaRemoteFetchWarnings.NEXT_CURSOR_SANITIZED);
        warnings.add(ScimDeltaRemoteFetchWarnings.RAW_CURSOR_SUPPRESSED);
      }
      return new SanitizedPage(count, validShape, nextCursor, continuation, warnings);
    } catch (Exception ignored) {
      return new SanitizedPage(0, false, false, Optional.empty(), warnings);
    }
  }

  public static SanitizedPage sanitize(String rawBody) {
    return sanitize(rawBody, ScimDeltaProviderKind.GENERIC);
  }

  private static int extractCount(JsonNode root) {
    if (root.has("totalResults") && root.get("totalResults").canConvertToInt()) {
      return Math.max(0, root.get("totalResults").asInt());
    }
    if (root.has("Resources") && root.get("Resources").isArray()) {
      return root.get("Resources").size();
    }
    return 0;
  }

  private static Optional<ScimDeltaPaginationContinuation> extractContinuation(
      JsonNode root, ScimDeltaProviderKind providerKind, List<String> warnings) {
    if (root.has("nextCursor") && root.get("nextCursor").isTextual()) {
      String cursor = root.get("nextCursor").asText("");
      if (!cursor.isBlank()) {
        return Optional.of(ScimDeltaPaginationContinuation.skipToken(cursor));
      }
    }

    if (root.has("@odata.nextLink") && root.get("@odata.nextLink").isTextual()) {
      return sanitizeNextLink(root.get("@odata.nextLink").asText(), warnings);
    }

    if (root.has("nextLink") && root.get("nextLink").isTextual()) {
      return sanitizeNextLink(root.get("nextLink").asText(), warnings);
    }

    int itemsPerPage = root.has("itemsPerPage") ? root.get("itemsPerPage").asInt(0) : 0;
    int startIndex = root.has("startIndex") ? root.get("startIndex").asInt(1) : 1;
    int resources =
        root.has("Resources") && root.get("Resources").isArray() ? root.get("Resources").size() : 0;
    if (itemsPerPage > 0 && resources >= itemsPerPage) {
      return Optional.of(ScimDeltaPaginationContinuation.startIndex(startIndex + itemsPerPage));
    }

    if (providerKind == ScimDeltaProviderKind.GENERIC && resources > 0 && itemsPerPage == 0) {
      return Optional.of(ScimDeltaPaginationContinuation.startIndex(startIndex + resources));
    }

    return Optional.empty();
  }

  private static Optional<ScimDeltaPaginationContinuation> sanitizeNextLink(
      String nextLink, List<String> warnings) {
    if (nextLink == null || nextLink.isBlank()) {
      return Optional.empty();
    }
    try {
      URI uri = URI.create(nextLink.trim());
      String path = uri.getRawPath() == null ? "" : uri.getRawPath();
      String query = uri.getRawQuery();
      String pathAndQuery = query == null || query.isBlank() ? path : path + "?" + query;
      if (pathAndQuery.isBlank()) {
        return Optional.empty();
      }
      warnings.add(ScimDeltaRemoteFetchWarnings.RAW_CURSOR_SUPPRESSED);
      return Optional.of(ScimDeltaPaginationContinuation.nextLinkPathQuery(pathAndQuery));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }
}
