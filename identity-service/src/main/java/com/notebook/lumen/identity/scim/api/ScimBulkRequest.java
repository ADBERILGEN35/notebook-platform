package com.notebook.lumen.identity.scim.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScimBulkRequest(
    List<String> schemas,
    Integer failOnErrors,
    @JsonProperty("Operations") List<Operation> operations) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Operation(String method, String path, String bulkId, JsonNode data) {}
}
