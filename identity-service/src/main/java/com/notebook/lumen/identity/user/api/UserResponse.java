package com.notebook.lumen.identity.user.api;

import com.notebook.lumen.identity.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String name,
    String avatarUrl,
    UserStatus status,
    Instant emailVerifiedAt,
    Instant createdAt) {}
