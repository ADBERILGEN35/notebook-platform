package com.notebook.lumen.notification.shared.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.notification.shared.config.NotificationProperties;
import com.notebook.lumen.notification.shared.exception.NotificationException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class InternalNotificationAuthorizerTest {
  private final KeyPair keyPair = keyPair();
  private final InternalNotificationAuthorizer authorizer =
      new InternalNotificationAuthorizer(properties(publicPem()));

  @Test
  void acceptsServiceJwtWithRequiredScopeAndAudience() {
    authorizer.authorize(
        "Bearer " + serviceToken("notification-service", "internal:notification:email:send"));
  }

  @Test
  void rejectsMissingServiceJwt() {
    assertThatThrownBy(() -> authorizer.authorize(""))
        .isInstanceOf(NotificationException.class)
        .extracting("errorCode")
        .isEqualTo("NOTIFICATION_ACCESS_DENIED");
  }

  @Test
  void rejectsWrongScope() {
    assertThatThrownBy(
            () -> authorizer.authorize("Bearer " + serviceToken("notification-service", "other")))
        .isInstanceOf(NotificationException.class)
        .extracting("errorCode")
        .isEqualTo("NOTIFICATION_ACCESS_DENIED");
  }

  @Test
  void rejectsWrongAudience() {
    assertThatThrownBy(
            () ->
                authorizer.authorize(
                    "Bearer "
                        + serviceToken("workspace-service", "internal:notification:email:send")))
        .isInstanceOf(NotificationException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_SERVICE_AUDIENCE");
  }

  private String serviceToken(String audience, String scope) {
    return new ServiceJwtSigner(
            new ServiceJwtProperties(
                "workspace-key-1",
                privatePem(),
                "",
                "workspace-service",
                "service:workspace-service",
                "workspace-service",
                Duration.ofSeconds(60)))
        .sign(audience, scope);
  }

  private NotificationProperties properties(String publicKey) {
    return new NotificationProperties(
        "",
        new NotificationProperties.Email(
            "noop",
            "no-reply@example.com",
            true,
            5,
            60,
            3600,
            5000,
            25,
            300,
            new NotificationProperties.Smtp("localhost", 587, "", "", true),
            new NotificationProperties.GenericHttp("", "", "Authorization", 1000, 3000),
            new NotificationProperties.Webhooks(
                false, "generic-http", "", "X-Email-Signature", "X-Email-Timestamp", 300, false)),
        new NotificationProperties.Internal(
            new NotificationProperties.TrustedService(
                "workspace-key-1",
                publicKey,
                "",
                "workspace-service",
                "notification-service",
                5,
                "internal:notification:email:send"),
            null));
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
      KeyPair generated = generator.generateKeyPair();
      return new KeyPair(
          (RSAPublicKey) generated.getPublic(), (RSAPrivateKey) generated.getPrivate());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
