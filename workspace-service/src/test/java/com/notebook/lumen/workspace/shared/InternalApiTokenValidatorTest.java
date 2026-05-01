package com.notebook.lumen.workspace.shared;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class InternalApiTokenValidatorTest {
  private final KeyPair keyPair = keyPair();

  @Test
  void dualModeAcceptsServiceJwtAndStaticTokenFallback() {
    InternalApiTokenValidator validator =
        new InternalApiTokenValidator(
            new WorkspaceProperties(
                null,
                null,
                new WorkspaceProperties.Internal(
                    "",
                    "static-token",
                    "",
                    "dual",
                    new WorkspaceProperties.TrustedService(
                        "content-key-1",
                        publicPem(),
                        "",
                        "content-service",
                        "workspace-service",
                        5,
                        "internal:workspace:permission:read"))));
    String token =
        new ServiceJwtSigner(
                new ServiceJwtProperties(
                    "content-key-1",
                    privatePem(),
                    "",
                    "content-service",
                    "service:content-service",
                    "content-service",
                    Duration.ofSeconds(60)))
            .sign("workspace-service", "internal:workspace:permission:read");

    assertThatCode(
            () -> validator.validate(null, "Bearer " + token, "internal:workspace:permission:read"))
        .doesNotThrowAnyException();
    assertThatCode(
            () -> validator.validate("static-token", null, "internal:workspace:permission:read"))
        .doesNotThrowAnyException();
  }

  private String privatePem() {
    return pem("PRIVATE KEY", keyPair.getPrivate().getEncoded());
  }

  private String publicPem() {
    return pem("PUBLIC KEY", keyPair.getPublic().getEncoded());
  }

  private String pem(String type, byte[] der) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
        + "\n-----END "
        + type
        + "-----";
  }

  private KeyPair keyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
