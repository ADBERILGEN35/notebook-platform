package com.notebook.lumen.search.reindex.client;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.search.shared.config.SearchProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class ContentSearchIndexSourceClientConfig {
  private static final String SOURCE_SCOPE = "internal:content:search-index-source:read";

  @Bean
  ContentSearchIndexSourceClient contentSearchIndexSourceClient(SearchProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(Duration.ofMillis(properties.contentSource().timeoutMs()));
    ServiceJwtSigner signer = signer(properties.serviceJwt());
    RestClient restClient =
        RestClient.builder()
            .baseUrl(properties.contentSource().serviceUrl())
            .requestFactory(requestFactory)
            .requestInterceptor(
                (request, body, execution) -> {
                  if (signer != null) {
                    request
                        .getHeaders()
                        .set(
                            "X-Service-Authorization",
                            "Bearer "
                                + signer.sign(properties.contentSource().audience(), SOURCE_SCOPE));
                  }
                  return execution.execute(request, body);
                })
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(ContentSearchIndexSourceClient.class);
  }

  private ServiceJwtSigner signer(SearchProperties.ServiceJwt jwt) {
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
            Duration.ofSeconds(jwt.ttlSeconds() <= 0 ? 60 : jwt.ttlSeconds())));
  }
}
