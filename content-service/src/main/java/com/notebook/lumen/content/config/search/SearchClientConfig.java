package com.notebook.lumen.content.config.search;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtProperties;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.content.client.search.SearchClient;
import com.notebook.lumen.content.config.ContentProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class SearchClientConfig {
  private static final String INDEX_SCOPE = "internal:search:index:write";

  @Bean
  SearchClient searchClient(ContentProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(Duration.ofMillis(properties.search().timeoutMs()));
    ServiceJwtSigner signer = signer(properties.search().serviceJwt());
    RestClient restClient =
        RestClient.builder()
            .baseUrl(properties.search().serviceUrl())
            .requestFactory(requestFactory)
            .requestInterceptor(
                (request, body, execution) -> {
                  if (signer != null) {
                    request
                        .getHeaders()
                        .set(
                            "X-Service-Authorization",
                            "Bearer "
                                + signer.sign(
                                    properties.search().serviceJwt().audience(), INDEX_SCOPE));
                  }
                  return execution.execute(request, body);
                })
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(SearchClient.class);
  }

  private ServiceJwtSigner signer(ContentProperties.SearchServiceJwt jwt) {
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
