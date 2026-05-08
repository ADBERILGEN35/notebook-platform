package com.notebook.lumen.identity.scim.api;

import java.util.List;

public record ScimGroupResponse(List<String> schemas, String id, String externalId, String displayName) {
  public static final String CORE_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group";

  public ScimGroupResponse(String id, String externalId, String displayName) {
    this(List.of(CORE_SCHEMA), id, externalId, displayName);
  }
}
