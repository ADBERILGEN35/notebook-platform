package com.notebook.lumen.identity.sso.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.identity.shared.exception.SsoException;
import com.notebook.lumen.identity.sso.SsoProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class OidcClient {
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public OidcClient(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.restClient = RestClient.builder().build();
  }

  public OidcProviderMetadata discover(SsoProperties.Provider provider) {
    try {
      String issuer = trimTrailingSlash(provider.issuerUri());
      JsonNode config =
          restClient
              .get()
              .uri(issuer + "/.well-known/openid-configuration")
              .retrieve()
              .body(JsonNode.class);
      if (config == null) {
        throw new SsoException(
            "SSO_PROVIDER_NOT_FOUND", HttpStatus.BAD_REQUEST, "OIDC provider metadata is unavailable");
      }
      return new OidcProviderMetadata(
          text(config, "authorization_endpoint"),
          text(config, "token_endpoint"),
          text(config, "jwks_uri"),
          text(config, "issuer"));
    } catch (SsoException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new SsoException(
          "SSO_PROVIDER_NOT_FOUND", HttpStatus.BAD_REQUEST, "OIDC provider metadata discovery failed");
    }
  }

  public OidcIdTokenProfile exchangeAndValidate(
      SsoProperties.Provider provider, OidcProviderMetadata metadata, String code, String redirectUri) {
    try {
      MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
      body.add("grant_type", "authorization_code");
      body.add("code", code);
      body.add("redirect_uri", redirectUri);
      body.add("client_id", provider.clientId());
      body.add("client_secret", provider.clientSecret());

      JsonNode token =
          restClient
              .post()
              .uri(metadata.tokenEndpoint())
              .body(body)
              .retrieve()
              .body(JsonNode.class);
      if (token == null || token.path("id_token").asText("").isBlank()) {
        throw new SsoException(
            "SSO_TOKEN_EXCHANGE_FAILED", HttpStatus.UNAUTHORIZED, "OIDC token exchange failed");
      }
      String idToken = token.path("id_token").asText();
      NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(metadata.jwksUri()).build();
      Jwt jwt = decoder.decode(idToken);
      validateIssuerAndAudience(jwt, metadata.issuer(), provider.clientId());
      Map<String, Object> claims = jwt.getClaims();

      String subject = jwt.getSubject();
      String email = claimAsString(claims, provider.emailClaim());
      boolean emailVerified = claimAsBoolean(claims, "email_verified");
      List<String> groups = claimAsList(claims, provider.groupsClaim());
      List<String> amr = claimAsList(claims, "amr");
      String acr = claimAsString(claims, "acr");
      String nonce = claimAsString(claims, "nonce");
      String sanitized = sanitizeClaims(claims);

      return new OidcIdTokenProfile(subject, email, emailVerified, groups, amr, acr, nonce, sanitized);
    } catch (SsoException ex) {
      throw ex;
    } catch (JwtException ex) {
      throw new SsoException("SSO_ID_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "OIDC id_token is invalid");
    } catch (Exception ex) {
      throw new SsoException(
          "SSO_TOKEN_EXCHANGE_FAILED", HttpStatus.UNAUTHORIZED, "OIDC token exchange failed");
    }
  }

  private void validateIssuerAndAudience(Jwt jwt, String expectedIssuer, String expectedAudience) {
    if (jwt.getIssuer() == null
        || expectedIssuer == null
        || !expectedIssuer.equals(jwt.getIssuer().toString())) {
      throw new SsoException("SSO_ID_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "OIDC issuer mismatch");
    }
    List<String> audience = jwt.getAudience();
    if (audience == null || !audience.contains(expectedAudience)) {
      throw new SsoException("SSO_ID_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "OIDC audience mismatch");
    }
    if (jwt.getExpiresAt() == null || jwt.getExpiresAt().isBefore(Instant.now())) {
      throw new SsoException("SSO_ID_TOKEN_INVALID", HttpStatus.UNAUTHORIZED, "OIDC token expired");
    }
  }

  private String sanitizeClaims(Map<String, Object> claims) {
    try {
      var safe = new java.util.LinkedHashMap<String, Object>();
      Set<String> blocked = Set.of("nonce", "at_hash", "c_hash", "sid", "jti");
      for (Map.Entry<String, Object> entry : claims.entrySet()) {
        String key = entry.getKey();
        if (blocked.contains(key)) {
          continue;
        }
        safe.put(key, entry.getValue());
      }
      return objectMapper.writeValueAsString(safe);
    } catch (Exception ex) {
      return "{}";
    }
  }

  private static String text(JsonNode node, String field) {
    return node.path(field).asText("");
  }

  private static String trimTrailingSlash(String input) {
    if (input == null) {
      return "";
    }
    return input.endsWith("/") ? input.substring(0, input.length() - 1) : input;
  }

  private static String claimAsString(Map<String, Object> claims, String key) {
    Object value = claims.get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private static boolean claimAsBoolean(Map<String, Object> claims, String key) {
    Object value = claims.get(key);
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String str) {
      return "true".equalsIgnoreCase(str);
    }
    return false;
  }

  private static List<String> claimAsList(Map<String, Object> claims, String key) {
    Object value = claims.get(key);
    if (value instanceof List<?> list) {
      return list.stream().map(String::valueOf).toList();
    }
    if (value instanceof String raw) {
      String[] parts = raw.split(",");
      List<String> out = new ArrayList<>();
      for (String part : parts) {
        String trimmed = part.trim();
        if (!trimmed.isBlank()) {
          out.add(trimmed);
        }
      }
      return out;
    }
    return List.of();
  }

  public URI buildAuthorizeUri(
      OidcProviderMetadata metadata,
      SsoProperties.Provider provider,
      String state,
      String nonce,
      String redirectUri) {
    String scopes = String.join("%20", provider.scopeSet());
    String encodedRedirect = encode(redirectUri);
    String uri =
        metadata.authorizationEndpoint()
            + "?response_type=code&client_id="
            + encode(provider.clientId())
            + "&redirect_uri="
            + encodedRedirect
            + "&scope="
            + scopes
            + "&state="
            + encode(state)
            + "&nonce="
            + encode(nonce);
    return URI.create(uri);
  }

  private String encode(String value) {
    return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
  }
}
