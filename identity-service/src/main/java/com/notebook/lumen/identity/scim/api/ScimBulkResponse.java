package com.notebook.lumen.identity.scim.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScimBulkResponse(
    List<String> schemas, @JsonProperty("Operations") List<OperationResult> operations) {
  public static final String SCHEMA = "urn:ietf:params:scim:api:messages:2.0:BulkResponse";

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record OperationResult(
      String method,
      String bulkId,
      String location,
      String status,
      JsonNode response,
      JsonNode errors) {}
}
