package com.notebook.lumen.identity.scim.api;

import java.util.List;

public record ScimListResponse<T>(
    List<String> schemas, int totalResults, int startIndex, int itemsPerPage, List<T> Resources) {
  public static final String SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";

  public static <T> ScimListResponse<T> of(
      int totalResults, int startIndex, int itemsPerPage, List<T> resources) {
    return new ScimListResponse<>(
        List.of(SCHEMA),
        totalResults,
        startIndex,
        itemsPerPage,
        resources == null ? List.of() : resources);
  }
}
