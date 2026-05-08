package com.notebook.lumen.identity.sso.application;

import com.notebook.lumen.identity.auth.api.AuthResponse;

public record SsoCallbackResult(AuthResponse authResponse, String returnUrl) {}
