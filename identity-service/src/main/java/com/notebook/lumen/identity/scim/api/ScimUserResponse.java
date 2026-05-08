package com.notebook.lumen.identity.scim.api;

import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import java.time.Instant;
import java.util.List;

public record ScimUserResponse(
    List<String> schemas,
    String id,
    String userName,
    Name name,
    String displayName,
    List<Email> emails,
    boolean active,
    String externalId,
    List<GroupRef> groups,
    Meta meta) {
  public static final String CORE_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";

  public record Name(String givenName, String familyName) {}

  public record Email(String value, String type, boolean primary) {}

  public record GroupRef(String value, String display) {}

  public record Meta(String resourceType, Instant created, Instant lastModified) {}

  public static ScimUserResponse fromUser(User user, List<GroupRef> groups) {
    return new ScimUserResponse(
        List.of(CORE_SCHEMA),
        user.getId().toString(),
        user.getEmail(),
        new Name("", ""),
        user.getName(),
        List.of(new Email(user.getEmail(), "work", true)),
        user.getStatus() == UserStatus.ACTIVE,
        user.getScimExternalId(),
        groups == null ? List.of() : groups,
        new Meta("User", user.getCreatedAt(), user.getUpdatedAt()));
  }
}
