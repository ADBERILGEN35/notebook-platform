package com.notebook.lumen.identity.admin.rbac.api;

import java.time.Instant;
import java.util.List;

public final class AdminRbacVisibilityDtos {
  private AdminRbacVisibilityDtos() {}

  public record RoleSource(String type, String sourceName, List<String> roles) {}

  public record UserListItem(
      String userId,
      String email,
      String status,
      List<String> platformRoles,
      List<String> platformPermissions,
      List<RoleSource> sources,
      Instant lastLoginAt,
      boolean mfaVerifiedRecently,
      List<String> warnings) {}

  public record UserListResponse(List<UserListItem> items, int page, int size, long totalElements) {}

  public record UserDetailResponse(UserListItem user, long pendingChangeRequestCount) {}
}
