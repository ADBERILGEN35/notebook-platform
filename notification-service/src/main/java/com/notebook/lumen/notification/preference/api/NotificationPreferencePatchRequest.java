package com.notebook.lumen.notification.preference.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record NotificationPreferencePatchRequest(
    @NotEmpty List<@Valid NotificationPreferenceUpdateItem> updates) {}
