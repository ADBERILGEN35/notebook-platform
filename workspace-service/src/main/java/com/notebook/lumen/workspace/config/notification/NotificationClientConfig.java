package com.notebook.lumen.workspace.config.notification;

import com.notebook.lumen.workspace.client.NotificationClient;
import com.notebook.lumen.workspace.config.WorkspaceProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class NotificationClientConfig {
  @Bean
  NotificationClient notificationClient(WorkspaceProperties properties) {
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(Duration.ofMillis(properties.notification().timeoutMs()));
    var headers = new NotificationServiceAuthHeaders(properties);
    RestClient restClient =
        RestClient.builder()
            .baseUrl(properties.notification().serviceUrl())
            .requestFactory(requestFactory)
            .requestInterceptor(
                (request, body, execution) -> {
                  headers.apply(request.getHeaders());
                  return execution.execute(request, body);
                })
            .build();
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(NotificationClient.class);
  }
}
