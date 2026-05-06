package com.notebook.lumen.notification.template.application;

import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class EmailTemplateRenderer {
  private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)}}");
  private static final Set<String> ALLOWED_TEMPLATES =
      Set.of("workspace-invitation", "security-refresh-tokens-revoked");

  public RenderedEmail render(String templateKey, Map<String, String> variables) {
    if (!ALLOWED_TEMPLATES.contains(templateKey)) {
      throw new NotificationException(
          HttpStatus.BAD_REQUEST, "EMAIL_TEMPLATE_NOT_FOUND", "Email template not found");
    }
    String text = renderResource(templateKey + ".txt", variables, false);
    String html = renderResource(templateKey + ".html", variables, true);
    return new RenderedEmail(text, html);
  }

  private String renderResource(String fileName, Map<String, String> variables, boolean html) {
    String template = read(fileName);
    Matcher matcher = VARIABLE.matcher(template);
    StringBuffer rendered = new StringBuffer();
    while (matcher.find()) {
      String variableName = matcher.group(1);
      String value = variables.get(variableName);
      if (value == null) {
        throw new NotificationException(
            HttpStatus.BAD_REQUEST,
            "INVALID_NOTIFICATION_REQUEST",
            "Missing email template variable: " + variableName);
      }
      matcher.appendReplacement(
          rendered, Matcher.quoteReplacement(html ? escapeHtml(value) : value));
    }
    matcher.appendTail(rendered);
    return rendered.toString();
  }

  private String read(String fileName) {
    try {
      ClassPathResource resource = new ClassPathResource("templates/email/" + fileName);
      if (!resource.exists()) {
        throw new NotificationException(
            HttpStatus.BAD_REQUEST, "EMAIL_TEMPLATE_NOT_FOUND", "Email template not found");
      }
      return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new NotificationException(
          HttpStatus.INTERNAL_SERVER_ERROR, "EMAIL_TEMPLATE_NOT_FOUND", "Email template not found");
    }
  }

  private String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  public record RenderedEmail(String bodyText, String bodyHtml) {}
}
