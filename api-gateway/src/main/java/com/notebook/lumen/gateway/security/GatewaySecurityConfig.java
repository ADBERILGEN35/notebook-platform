package com.notebook.lumen.gateway.security;

import com.notebook.lumen.gateway.config.GatewayJwtProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.GatewayErrorResponseWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import reactor.core.publisher.Mono;

@Configuration
public class GatewaySecurityConfig {

  private static final OAuth2Error INVALID_TOKEN_TYPE =
      new OAuth2Error("invalid_token", ErrorCode.INVALID_TOKEN_TYPE.name(), null);

  @Bean
  ReactiveJwtDecoder jwtDecoder(
      GatewayJwtProperties jwtProperties, JwtPublicKeyLoader publicKeyLoader) {
    NimbusReactiveJwtDecoder decoder;
    if (jwtProperties.jwksUri() != null && !jwtProperties.jwksUri().isBlank()) {
      decoder =
          NimbusReactiveJwtDecoder.withJwkSetUri(jwtProperties.jwksUri())
              .jwsAlgorithm(SignatureAlgorithm.RS256)
              .build();
    } else {
      decoder =
          NimbusReactiveJwtDecoder.withPublicKey(publicKeyLoader.load())
              .signatureAlgorithm(SignatureAlgorithm.RS256)
              .build();
    }

    OAuth2TokenValidator<Jwt> tokenTypeValidator =
        jwt -> {
          String tokenType = jwt.getClaimAsString("token_type");
          if ("access".equals(tokenType)) {
            return OAuth2TokenValidatorResult.success();
          }
          return OAuth2TokenValidatorResult.failure(INVALID_TOKEN_TYPE);
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), tokenTypeValidator));
    return decoder;
  }

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http, ServerAuthenticationEntryPoint authenticationEntryPoint) {
    return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .authorizeExchange(
            exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.POST, "/auth/signup", "/auth/login", "/auth/refresh")
                    .permitAll()
                    .pathMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .pathMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/webjars/swagger-ui/**")
                    .permitAll()
                    .anyExchange()
                    .authenticated())
        .oauth2ResourceServer(
            oauth2 ->
                oauth2
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(this::convertJwt)))
        .build();
  }

  @Bean
  ServerAuthenticationEntryPoint authenticationEntryPoint(
      GatewayErrorResponseWriter errorResponseWriter) {
    return (exchange, ex) -> {
      String authorization = exchange.getRequest().getHeaders().getFirst("Authorization");
      if (authorization == null || authorization.isBlank()) {
        return errorResponseWriter.write(
            exchange,
            HttpStatus.UNAUTHORIZED,
            ErrorCode.MISSING_ACCESS_TOKEN,
            "Missing access token");
      }

      String message = ex.getMessage() == null ? "" : ex.getMessage();
      if (message.contains(ErrorCode.INVALID_TOKEN_TYPE.name())) {
        return errorResponseWriter.write(
            exchange, HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_TOKEN_TYPE, "Invalid token type");
      }
      if (message.toLowerCase().contains("expired")) {
        return errorResponseWriter.write(
            exchange,
            HttpStatus.UNAUTHORIZED,
            ErrorCode.EXPIRED_ACCESS_TOKEN,
            "Access token expired");
      }
      return errorResponseWriter.write(
          exchange,
          HttpStatus.UNAUTHORIZED,
          ErrorCode.INVALID_ACCESS_TOKEN,
          "Invalid access token");
    };
  }

  private Mono<AbstractAuthenticationToken> convertJwt(Jwt jwt) {
    return Mono.just(
        new JwtAuthenticationToken(
            jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")), jwt.getSubject()));
  }
}
