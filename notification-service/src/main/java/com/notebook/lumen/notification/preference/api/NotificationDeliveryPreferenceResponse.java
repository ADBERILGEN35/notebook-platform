package com.notebook.lumen.notification.preference.api;

import com.notebook.lumen.notification.preference.domain.EmailDigestFrequency;
import java.time.LocalTime;

public record NotificationDeliveryPreferenceResponse(
    boolean emailDigestEnabled,
    EmailDigestFrequency emailDigestFrequency,
    boolean quietHoursEnabled,
    LocalTime quietHoursStart,
    LocalTime quietHoursEnd,
    String timezone) {}
