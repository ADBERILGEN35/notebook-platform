package com.notebook.lumen.identity.scim.sync.delta;

import com.notebook.lumen.identity.scim.sync.ScimDeltaProviderKind;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Internal pagination state for the next GET (Faz 119). Raw cursor/token values are never exposed
 * via API, logs, or {@link #toString()}.
 */
final class ScimDeltaPaginationContinuation {

  enum Kind {
    START_INDEX,
    SKIP_TOKEN,
    NEXT_LINK_PATH_QUERY
  }

  private final Kind kind;
  private final int nextStartIndex;
  private final String skipToken;
  private final String pathAndQuery;

  private ScimDeltaPaginationContinuation(
      Kind kind, int nextStartIndex, String skipToken, String pathAndQuery) {
    this.kind = kind;
    this.nextStartIndex = nextStartIndex;
    this.skipToken = skipToken;
    this.pathAndQuery = pathAndQuery;
  }

  static ScimDeltaPaginationContinuation startIndex(int nextStartIndex) {
    return new ScimDeltaPaginationContinuation(Kind.START_INDEX, nextStartIndex, null, null);
  }

  static ScimDeltaPaginationContinuation skipToken(String token) {
    return new ScimDeltaPaginationContinuation(Kind.SKIP_TOKEN, 0, token, null);
  }

  static ScimDeltaPaginationContinuation nextLinkPathQuery(String pathAndQuery) {
    return new ScimDeltaPaginationContinuation(Kind.NEXT_LINK_PATH_QUERY, 0, null, pathAndQuery);
  }

  String buildRequestUri(
      String baseUrl, String resourcePath, int pageSize, ScimDeltaProviderKind providerKind) {
    String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return switch (kind) {
      case START_INDEX ->
          switch (providerKind) {
            case AZURE_AD ->
                base + resourcePath + "?$top=" + pageSize + "&$skip=" + (nextStartIndex - 1);
            default -> base + resourcePath + "?count=" + pageSize + "&startIndex=" + nextStartIndex;
          };
      case SKIP_TOKEN ->
          switch (providerKind) {
            case AZURE_AD ->
                base + resourcePath + "?$top=" + pageSize + "&$skiptoken=" + encode(skipToken);
            case OKTA ->
                base
                    + resourcePath
                    + "?count="
                    + pageSize
                    + "&startIndex=1&skiptoken="
                    + encode(skipToken);
            default ->
                base
                    + resourcePath
                    + "?count="
                    + pageSize
                    + "&startIndex=1&skiptoken="
                    + encode(skipToken);
          };
      case NEXT_LINK_PATH_QUERY -> {
        String pq = pathAndQuery.startsWith("/") ? pathAndQuery : "/" + pathAndQuery;
        yield base + pq;
      }
    };
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "ScimDeltaPaginationContinuation[redacted]";
  }
}
