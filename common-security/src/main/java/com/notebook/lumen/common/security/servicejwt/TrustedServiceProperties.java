package com.notebook.lumen.common.security.servicejwt;

import java.time.Duration;
import java.util.Set;

public record TrustedServiceProperties(
    String kid,
    String publicKey,
    String publicKeyPath,
    String issuer,
    String audience,
    Duration clockSkew,
    Set<String> allowedScopes) {}
