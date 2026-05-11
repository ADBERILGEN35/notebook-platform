package com.notebook.lumen.identity.notification;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class NotificationClientConfig {
  private static final String EMAIL_SCOPE = "internal:notification:email:send";
  private static final String IN_APP_SCOPE = "internal:notification:in-app:create";

  @Bean
  NotificationClient identityNotificationClient(IdentityNotificationProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(
        Duration.ofMillis(properties.timeoutMs() <= 0 ? 1000 : properties.timeoutMs()));
    ServiceJwtSigner signer = signer(properties.serviceJwt());
    RestClient restClient =
        RestClient.builder()
            .baseUrl(properties.serviceUrl())
            .requestFactory(requestFactory)
            .requestInterceptor(
                (request, body, execution) -> {
                  apply(request.getHeaders(), request.getURI().getPath(), signer, properties);
                  return execution.execute(request, body);
                })
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(NotificationClient.class);
  }

  private void apply(
      HttpHeaders headers,
      String path,
      ServiceJwtSigner signer,
      IdentityNotificationProperties properties) {
    if (signer != null) {
      String scope =
          path != null && path.contains("/internal/notifications/in-app")
              ? IN_APP_SCOPE
              : EMAIL_SCOPE;
      headers.set(
          "X-Service-Authorization",
          "Bearer " + signer.sign(properties.serviceJwt().audience(), scope));
    }
  }

  private ServiceJwtSigner signer(IdentityNotificationProperties.ServiceJwt jwt) {
    if (jwt == null || !jwt.signingConfigured()) {
      return null;
    }
    return new ServiceJwtSigner(
        new ServiceJwtProperties(
            jwt.activeKid(),
            jwt.privateKey(),
            jwt.privateKeyPath(),
            jwt.issuer(),
            jwt.subject(),
            jwt.serviceName(),
            jwt.ttl()));
  }
}
