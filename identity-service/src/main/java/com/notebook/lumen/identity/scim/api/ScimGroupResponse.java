package com.notebook.lumen.identity.scim.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimGroupResponse(
    List<String> schemas,
    String id,
    String externalId,
    String displayName,
    Boolean active,
    List<Member> members,
    Meta meta) {
  public static final String CORE_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Member(
      String value,
      @JsonProperty("$ref") String ref,
      String display,
      String type) {}

  public record Meta(String resourceType, Instant created, Instant lastModified) {}

  public static ScimGroupResponse summary(
      String id, String externalId, String displayName, boolean active) {
    return new ScimGroupResponse(
        List.of(CORE_SCHEMA), id, externalId, displayName, active, null, null);
  }

  public static ScimGroupResponse withMembers(
      String id,
      String externalId,
      String displayName,
      boolean active,
      List<Member> members,
      Meta meta) {
    return new ScimGroupResponse(
        List.of(CORE_SCHEMA), id, externalId, displayName, active, members, meta);
  }
}
