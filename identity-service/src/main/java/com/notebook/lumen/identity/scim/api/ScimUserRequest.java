package com.notebook.lumen.identity.scim.api;

import java.util.List;

public record ScimUserRequest(
    String userName,
    Name name,
    String displayName,
    List<Email> emails,
    Boolean active,
    String externalId,
    List<GroupRef> groups) {
  public record Name(String givenName, String familyName) {}

  public record Email(String value, String type, Boolean primary) {}

  public record GroupRef(String value, String display) {}
}
