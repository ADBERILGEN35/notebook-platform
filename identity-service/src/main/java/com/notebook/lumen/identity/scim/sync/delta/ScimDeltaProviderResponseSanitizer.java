package com.notebook.lumen.identity.scim.sync.delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Extracts aggregate SCIM list diagnostics without retaining PII (Faz 117). */
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
      List<String> warnings) {}

  public static SanitizedPage sanitize(String rawBody) {
    List<String> warnings = new ArrayList<>();
    warnings.add(ScimDeltaRemoteFetchWarnings.PROVIDER_RESPONSE_SANITIZED);
    warnings.add("SCIM_DELTA_RAW_PAYLOAD_SUPPRESSED");

    if (rawBody == null || rawBody.isBlank()) {
      return new SanitizedPage(0, false, false, warnings);
    }

    if (FORBIDDEN_PAYLOAD_SNIPPETS.matcher(rawBody).find()) {
      return new SanitizedPage(0, false, false, warnings);
    }

    try {
      JsonNode root = MAPPER.readTree(rawBody);
      int count = extractCount(root);
      boolean validShape = root.has("schemas") || root.has("totalResults") || root.has("Resources");
      boolean nextCursor = detectNextCursor(root, rawBody);
      if (nextCursor) {
        warnings.add(ScimDeltaRemoteFetchWarnings.NEXT_CURSOR_PRESENT);
      }
      return new SanitizedPage(count, validShape, nextCursor, warnings);
    } catch (Exception ignored) {
      return new SanitizedPage(0, false, false, warnings);
    }
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

  private static boolean detectNextCursor(JsonNode root, String rawBody) {
    if (root.has("nextCursor") && !root.get("nextCursor").asText("").isBlank()) {
      return true;
    }
    String lower = rawBody.toLowerCase();
    return lower.contains("skiptoken") || lower.contains("\"nextlink\"");
  }
}
