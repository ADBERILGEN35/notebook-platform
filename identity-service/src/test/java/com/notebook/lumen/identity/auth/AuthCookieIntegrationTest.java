package com.notebook.lumen.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.identity.auth.api.LoginRequest;
import com.notebook.lumen.identity.auth.api.SignupRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "auth.token-transport=cookie",
      "auth.cookie-secure=false",
      "auth.cookie-same-site=Lax"
    })
@ActiveProfiles("test")
class AuthCookieIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("notebook_platform")
          .withUsername("notebook")
          .withPassword("notebook");

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @LocalServerPort private int port;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void login_setsAuthAndCsrfCookies() throws Exception {
    String email = "cookie-" + UUID.randomUUID() + "@example.com";
    String password = "Password1234";
    post("/auth/signup", new SignupRequest(email, password, "Cookie User", null));

    HttpResponse<String> response = post("/auth/login", new LoginRequest(email, password));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().allValues("Set-Cookie"))
        .anyMatch(value -> value.startsWith("__Host-np_access="))
        .anyMatch(value -> value.startsWith("__Host-np_refresh="))
        .anyMatch(value -> value.startsWith("NP-XSRF-TOKEN="));
    assertThat(response.body()).doesNotContain("\"accessToken\":\"");
  }

  private HttpResponse<String> post(String path, Object body) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
