package com.notebook.lumen.identity.scim.api;

import java.util.List;

public record ScimErrorResponse(List<String> schemas, String status, String scimType, String detail) {
  public static final String SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

  public static ScimErrorResponse of(int status, String scimType, String detail) {
    return new ScimErrorResponse(List.of(SCHEMA), String.valueOf(status), scimType, detail);
  }
}
