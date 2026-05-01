package com.notebook.lumen.common.security.servicejwt;

import java.time.Instant;
import java.util.UUID;

public record ServiceJwtClaims(
    String issuer,
    String subject,
    String audience,
    String scope,
    Instant issuedAt,
    Instant expiresAt,
    UUID jwtId,
    String serviceName) {}
