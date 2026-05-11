package com.notebook.lumen.workspace.config.search;

import com.notebook.lumen.workspace.client.SearchPermissionRefreshClient;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class SearchPermissionRefreshClientConfig {

  @Bean
  SearchPermissionRefreshClient searchPermissionRefreshClient(WorkspaceProperties properties) {
    WorkspaceProperties.Search search = properties.search();
    if (search == null || search.serviceUrl() == null || search.serviceUrl().isBlank()) {
      // Return a no-op implementation if service url is missing.
      return notebookId -> {};
    }

    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(
        Duration.ofMillis(search.timeoutMs() <= 0 ? 1000 : search.timeoutMs()));
    SearchServiceAuthHeaders authHeaders = new SearchServiceAuthHeaders(properties);

    RestClient restClient =
        RestClient.builder()
            .baseUrl(search.serviceUrl())
            .requestFactory(requestFactory)
            .requestInterceptor(
                (request, body, execution) -> {
                  authHeaders.apply(request.getHeaders());
                  return execution.execute(request, body);
                })
            .build();

    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(SearchPermissionRefreshClient.class);
  }
}
