package com.notebook.lumen.workspace.shared;

import com.notebook.lumen.common.security.secrets.SecretValue;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import com.notebook.lumen.workspace.shared.exception.Exceptions;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class InternalApiTokenValidator {
  public static final String HEADER_NAME = "X-Internal-Token";

  private final WorkspaceProperties properties;

  public InternalApiTokenValidator(WorkspaceProperties properties) {
    this.properties = properties;
  }

  public void validate(String providedToken) {
    WorkspaceProperties.Internal internal = properties.internal();
    if (internal == null || !internal.tokenRequired()) {
      return;
    }
    SecretValue primary = internal.effectivePrimarySecret();
    SecretValue secondary = internal.secondarySecret();
    if (!matches(providedToken, primary.value())
        && (!internal.secondaryTokenConfigured() || !matches(providedToken, secondary.value()))) {
      throw Exceptions.unauthorized("INTERNAL_TOKEN_REQUIRED", "Internal API token is required");
    }
  }

  private boolean matches(String providedToken, String expectedToken) {
    if (providedToken == null || expectedToken == null || expectedToken.isBlank()) {
      return false;
    }
    byte[] provided = providedToken.getBytes(StandardCharsets.UTF_8);
    byte[] expected = expectedToken.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(provided, expected);
  }
}
