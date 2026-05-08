package com.notebook.lumen.notification.preference.api;

import com.notebook.lumen.notification.preference.domain.EmailDigestFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record NotificationDeliveryPreferenceRequest(
    boolean emailDigestEnabled,
    @NotNull EmailDigestFrequency emailDigestFrequency,
    boolean quietHoursEnabled,
    LocalTime quietHoursStart,
    LocalTime quietHoursEnd,
    @NotBlank String timezone) {}
