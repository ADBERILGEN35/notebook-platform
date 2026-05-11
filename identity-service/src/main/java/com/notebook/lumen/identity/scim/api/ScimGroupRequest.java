package com.notebook.lumen.identity.scim.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScimGroupRequest(String externalId, String displayName, List<Member> members) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Member(
      String value, String display, String type, @JsonProperty("$ref") String ref) {}
}
