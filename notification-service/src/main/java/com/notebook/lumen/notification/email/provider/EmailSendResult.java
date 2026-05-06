package com.notebook.lumen.notification.email.provider;

import java.time.Instant;

public record EmailSendResult(String provider, String providerMessageId, Instant acceptedAt) {}
