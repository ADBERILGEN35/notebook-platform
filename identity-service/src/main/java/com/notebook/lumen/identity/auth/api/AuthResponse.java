package com.notebook.lumen.identity.auth.api;

import com.notebook.lumen.identity.user.api.UserResponse;
import java.util.List;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserResponse user,
    boolean mfaRequired,
    String mfaSessionId,
    List<String> availableMethods) {}
