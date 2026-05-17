package com.notebook.lumen.identity.scim.sync.delta;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.identity.scim.ScimProperties;
import com.notebook.lumen.identity.scim.sync.ScimProviderErrorClass;
import com.notebook.lumen.identity.scim.sync.ScimResourceType;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultScimDeltaProviderClientTest {

  private HttpServer server;
  private String baseUrl;
  private final AtomicReference<String> observedMethod = new AtomicReference<>();

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/Users",
        exchange -> {
          observedMethod.set(exchange.getRequestMethod());
          byte[] body =
              "{\"totalResults\":3,\"Resources\":[{}, {}, {}]}".getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/scim+json");
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
          }
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void successfulGetReturnsAggregateCountOnly() {
    var client = new DefaultScimDeltaProviderClient(properties());
    var request =
        new ScimDeltaProviderRequest(
            ScimDeltaHttpMethod.GET, baseUrl + "/Users?count=10&startIndex=1", ScimResourceType.USER, 10);

    var result = client.fetch(request, "test-bearer-token");

    assertThat(observedMethod.get()).isEqualTo("GET");
    assertThat(result.fetchedResourceCount()).isEqualTo(3);
    assertThat(result.providerErrorClass()).isEqualTo(ScimProviderErrorClass.NONE);
    assertThat(result.warnings()).contains(ScimDeltaRemoteFetchWarnings.REMOTE_FETCH_ATTEMPTED);
  }

  @Test
  void rateLimitedUsesClassifier() throws IOException {
    server.stop(0);
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/Users",
        exchange -> {
          observedMethod.set(exchange.getRequestMethod());
          exchange.getResponseHeaders().add("Retry-After", "90");
          exchange.sendResponseHeaders(429, -1);
          exchange.close();
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();

    var client = new DefaultScimDeltaProviderClient(properties());
    var request =
        new ScimDeltaProviderRequest(
            ScimDeltaHttpMethod.GET, baseUrl + "/Users", ScimResourceType.USER, 10);

    var result = client.fetch(request, "token");

    assertThat(result.providerErrorClass()).isEqualTo(ScimProviderErrorClass.RATE_LIMITED);
    assertThat(result.retryAfterSeconds()).isEqualTo(90);
    assertThat(observedMethod.get()).isEqualTo("GET");
  }

  @Test
  void onlyGetHttpMethodIsExposed() {
    assertThat(ScimDeltaHttpMethod.values()).containsExactly(ScimDeltaHttpMethod.GET);
  }

  private static ScimProperties properties() {
    return new ScimProperties(
        true,
        "inbound",
        "",
        true,
        "notebook-admins",
        true,
        5,
        false,
        100,
        10,
        "generic",
        false,
        "disabled",
        false,
        true,
        true,
        false,
        true,
        100,
        true,
        true,
        true,
        3000,
        300,
        30,
        "",
        "",
        "",
        "",
        100);
  }
}
