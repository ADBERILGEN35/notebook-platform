package com.notebook.lumen.common.security.servicejwt;

import java.time.Duration;

public record ServiceJwtProperties(
    String activeKid,
    String privateKey,
    String privateKeyPath,
    String issuer,
    String subject,
    String serviceName,
    Duration ttl) {}
