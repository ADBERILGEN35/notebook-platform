package com.notebook.lumen.identity.sso.application;

import java.util.List;

public record OidcIdTokenProfile(
    String subject,
    String email,
    boolean emailVerified,
    List<String> groups,
    List<String> amr,
    String acr,
    String nonce,
    String sanitizedClaims) {}
