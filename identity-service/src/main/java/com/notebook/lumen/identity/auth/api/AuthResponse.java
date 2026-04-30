package com.notebook.lumen.identity.auth.api;

import com.notebook.lumen.identity.user.api.UserResponse;

public record AuthResponse(
    String accessToken, String refreshToken, String tokenType, long expiresIn, UserResponse user) {}
