package com.notebook.lumen.notification.email.suppression.api;

import com.notebook.lumen.notification.email.suppression.EmailSuppressionReason;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record EmailSuppressionCreateRequest(
    @NotBlank @Email String email, @NotNull EmailSuppressionReason reason, Instant expiresAt) {}
