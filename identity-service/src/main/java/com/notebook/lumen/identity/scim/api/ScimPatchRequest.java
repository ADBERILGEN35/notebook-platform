package com.notebook.lumen.identity.scim.api;

import java.util.List;
import java.util.Map;

public record ScimPatchRequest(List<String> schemas, List<Operation> operations) {
  public record Operation(String op, String path, Map<String, Object> value) {}

  @SuppressWarnings("unchecked")
  public static List<ScimUserRequest.Email> readEmails(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(
            m ->
                new ScimUserRequest.Email(
                    String.valueOf(m.get("value")),
                    String.valueOf(m.getOrDefault("type", "work")),
                    Boolean.valueOf(String.valueOf(m.getOrDefault("primary", true)))))
        .toList();
  }

  @SuppressWarnings("unchecked")
  public static List<ScimUserRequest.GroupRef> readGroups(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    return list.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(
            m ->
                new ScimUserRequest.GroupRef(
                    String.valueOf(m.get("value")), String.valueOf(m.getOrDefault("display", ""))))
        .toList();
  }
}
