package com.notebook.lumen.notification.user.api;

import java.util.List;

public record UserNotificationPageResponse(
    List<UserNotificationResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext,
    boolean hasPrevious) {}
