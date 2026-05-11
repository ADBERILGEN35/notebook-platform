package com.notebook.lumen.notification.admin.legalhold;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LegalHoldAdminDtos {

  private LegalHoldAdminDtos() {}

  public record LegalHoldCreateRequest(
      String holdKey, String scope, String reason, Instant expiresAt) {}

  public record LegalHoldReleaseRequest(String reason) {}

  public record LegalHoldResponse(
      UUID id,
      String holdKey,
      String scope,
      String status,
      Instant createdAt,
      Instant expiresAt,
      UUID createdByUserId,
      String createdByEmail,
      Instant releasedAt,
      UUID releasedByUserId) {}

  public record LegalHoldListResponse(List<LegalHoldResponse> holds) {}
}
