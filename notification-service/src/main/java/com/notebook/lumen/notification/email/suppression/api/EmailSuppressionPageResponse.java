package com.notebook.lumen.notification.email.suppression.api;

import java.util.List;

public record EmailSuppressionPageResponse(
    List<EmailSuppressionResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last) {}
