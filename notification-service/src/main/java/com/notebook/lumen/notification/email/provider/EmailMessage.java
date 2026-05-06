package com.notebook.lumen.notification.email.provider;

import java.util.Map;

public record EmailMessage(
    String recipient,
    String subject,
    String bodyText,
    String bodyHtml,
    Map<String, String> metadata) {}
