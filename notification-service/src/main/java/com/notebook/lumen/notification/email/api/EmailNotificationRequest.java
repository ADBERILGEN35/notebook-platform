package com.notebook.lumen.notification.email.api;

import com.notebook.lumen.notification.email.domain.EmailNotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record EmailNotificationRequest(
    @NotNull EmailNotificationType type,
    @NotBlank @Email String recipientEmail,
    @NotBlank @Size(max = 255) String subject,
    @NotBlank String templateKey,
    Map<String, String> templateVariables,
    @Size(max = 255) String idempotencyKey) {}
