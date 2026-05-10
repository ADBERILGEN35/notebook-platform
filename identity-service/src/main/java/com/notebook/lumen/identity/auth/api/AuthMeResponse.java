package com.notebook.lumen.identity.auth.api;

import java.util.List;
import java.util.UUID;

public record AuthMeResponse(
    UUID userId,
    String email,
    String name,
    String avatarUrl,
    List<String> roles,
    List<String> platformRoles,
    List<String> platformPermissions) {}
