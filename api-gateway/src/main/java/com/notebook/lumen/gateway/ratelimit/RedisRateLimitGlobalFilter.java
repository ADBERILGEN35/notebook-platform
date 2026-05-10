package com.notebook.lumen.gateway.ratelimit;

import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.GatewayErrorResponseWriter;
import java.net.InetSocketAddress;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RedisRateLimitGlobalFilter implements GlobalFilter, Ordered {

  private final RedisRateLimiter authRedisRateLimiter;
  private final RedisRateLimiter protectedRedisRateLimiter;
  private final RedisRateLimiter adminAuditRedisRateLimiter;
  private final RedisRateLimiter adminAuditExportRedisRateLimiter;
  private final RedisRateLimiter adminAuditExportMachineRedisRateLimiter;
  private final RedisRateLimiter scimRedisRateLimiter;
  private final RedisRateLimiter adminWriteRedisRateLimiter;
  private final GatewayErrorResponseWriter errorResponseWriter;

  public RedisRateLimitGlobalFilter(
      @Qualifier("authRedisRateLimiter") RedisRateLimiter authRedisRateLimiter,
      @Qualifier("protectedRedisRateLimiter") RedisRateLimiter protectedRedisRateLimiter,
      @Qualifier("adminAuditRedisRateLimiter") RedisRateLimiter adminAuditRedisRateLimiter,
      @Qualifier("adminAuditExportRedisRateLimiter") RedisRateLimiter adminAuditExportRedisRateLimiter,
      @Qualifier("adminAuditExportMachineRedisRateLimiter")
          RedisRateLimiter adminAuditExportMachineRedisRateLimiter,
      @Qualifier("scimRedisRateLimiter") RedisRateLimiter scimRedisRateLimiter,
      @Qualifier("adminWriteRedisRateLimiter") RedisRateLimiter adminWriteRedisRateLimiter,
      GatewayErrorResponseWriter errorResponseWriter) {
    this.authRedisRateLimiter = authRedisRateLimiter;
    this.protectedRedisRateLimiter = protectedRedisRateLimiter;
    this.adminAuditRedisRateLimiter = adminAuditRedisRateLimiter;
    this.adminAuditExportRedisRateLimiter = adminAuditExportRedisRateLimiter;
    this.adminAuditExportMachineRedisRateLimiter = adminAuditExportMachineRedisRateLimiter;
    this.scimRedisRateLimiter = scimRedisRateLimiter;
    this.adminWriteRedisRateLimiter = adminWriteRedisRateLimiter;
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
    String routeId = route == null ? "unknown" : route.getId();
    boolean authEndpoint = isPublicAuthEndpoint(exchange);
    if (isAdminAuditExportEndpoint(exchange)) {
      return exchange
          .getPrincipal()
          .cast(JwtAuthenticationToken.class)
          .map(java.util.Optional::of)
          .defaultIfEmpty(java.util.Optional.empty())
          .flatMap(
              authOpt -> {
                JwtAuthenticationToken auth = authOpt.orElse(null);
                boolean machine =
                    auth != null && "machine".equals(auth.getToken().getClaimAsString("token_type"));
                RedisRateLimiter limiter =
                    machine ? adminAuditExportMachineRedisRateLimiter : adminAuditExportRedisRateLimiter;
                Mono<String> rateKey =
                    machine
                        ? Mono.just(
                            "machine:"
                                + auth.getToken().getIssuer()
                                + ":"
                                + auth.getToken().getSubject())
                        : userId(exchange);
                return checkAllowed(exchange, chain, limiter, routeId, rateKey);
              });
    }
    if (isScimEndpoint(exchange)) {
      return checkAllowed(exchange, chain, scimRedisRateLimiter, routeId, Mono.just(clientIp(exchange)));
    }
    if (isAdminEnterpriseChangeRequestsEndpoint(exchange)) {
      return checkAllowed(exchange, chain, adminWriteRedisRateLimiter, routeId, userId(exchange));
    }
    if (isAdminNotificationDeadLetterRequeueEndpoint(exchange)) {
      return checkAllowed(exchange, chain, adminWriteRedisRateLimiter, routeId, userId(exchange));
    }
    if (isAdminNotificationRetentionDestructiveEndpoint(exchange)) {
      return checkAllowed(exchange, chain, adminWriteRedisRateLimiter, routeId, userId(exchange));
    }
    if (isAdminNotificationLegalHoldWriteEndpoint(exchange)) {
      return checkAllowed(exchange, chain, adminWriteRedisRateLimiter, routeId, userId(exchange));
    }

    RedisRateLimiter limiter;
    if (authEndpoint) {
      limiter = authRedisRateLimiter;
    } else if (isAdminAuditEndpoint(exchange)
        || isAdminEnterpriseStatusEndpoint(exchange)
        || isAdminNotificationAnalyticsEndpoint(exchange)
        || isAdminNotificationDeadLetterReadOrDryRunEndpoint(exchange)
        || isAdminNotificationRetentionReadEndpoint(exchange)
        || isAdminNotificationLegalHoldReadEndpoint(exchange)) {
      limiter = adminAuditRedisRateLimiter;
    } else {
      limiter = protectedRedisRateLimiter;
    }

    Mono<String> key = authEndpoint ? Mono.just(clientIp(exchange)) : userId(exchange);
    return checkAllowed(exchange, chain, limiter, routeId, key);
  }

  private Mono<Void> checkAllowed(
      ServerWebExchange exchange,
      GatewayFilterChain chain,
      RedisRateLimiter limiter,
      String routeId,
      Mono<String> key) {
    return key.flatMap(rateLimitKey -> limiter.isAllowed(routeId, rateLimitKey))
        .flatMap(
            response -> {
              response
                  .getHeaders()
                  .forEach((name, value) -> exchange.getResponse().getHeaders().set(name, value));
              if (response.isAllowed()) {
                return chain.filter(exchange);
              }
              return errorResponseWriter.write(
                  exchange,
                  HttpStatus.TOO_MANY_REQUESTS,
                  ErrorCode.RATE_LIMIT_EXCEEDED,
                  "Rate limit exceeded");
            });
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 30;
  }

  private boolean isPublicAuthEndpoint(ServerWebExchange exchange) {
    String path = exchange.getRequest().getPath().value();
    return "/auth/login".equals(path)
        || "/auth/signup".equals(path)
        || "/auth/refresh".equals(path);
  }

  private boolean isAdminAuditEndpoint(ServerWebExchange exchange) {
    return "/admin/audit-events".equals(exchange.getRequest().getPath().value());
  }

  private boolean isAdminEnterpriseStatusEndpoint(ServerWebExchange exchange) {
    return "/admin/enterprise/status".equals(exchange.getRequest().getPath().value());
  }

  private boolean isAdminNotificationAnalyticsEndpoint(ServerWebExchange exchange) {
    return exchange.getRequest().getPath().value().startsWith("/admin/notifications/analytics");
  }

  private boolean isAdminNotificationDeadLetterReadOrDryRunEndpoint(ServerWebExchange exchange) {
    String path = exchange.getRequest().getPath().value();
    if (!path.startsWith("/admin/notifications/dead-letter")) {
      return false;
    }
    var method = exchange.getRequest().getMethod();
    if (org.springframework.http.HttpMethod.GET.equals(method)) {
      return true;
    }
    return org.springframework.http.HttpMethod.POST.equals(method) && path.endsWith("/requeue/dry-run");
  }

  /** POST .../requeue only (not dry-run): stricter admin-write rate limit. */
  private boolean isAdminNotificationDeadLetterRequeueEndpoint(ServerWebExchange exchange) {
    if (!org.springframework.http.HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
      return false;
    }
    String path = exchange.getRequest().getPath().value();
    return path.startsWith("/admin/notifications/dead-letter/")
        && path.endsWith("/requeue")
        && !path.endsWith("/dry-run");
  }

  private boolean isAdminEnterpriseChangeRequestsEndpoint(ServerWebExchange exchange) {
    return exchange.getRequest().getPath().value().startsWith("/admin/enterprise/change-requests");
  }

  private boolean isAdminNotificationRetentionReadEndpoint(ServerWebExchange exchange) {
    return exchange.getRequest().getPath().value().startsWith("/admin/notifications/retention/plan");
  }

  /** POST .../retention/run with dryRun false is still the same path — body parsed in controller; use write bucket for all POST /run. */
  private boolean isAdminNotificationRetentionDestructiveEndpoint(ServerWebExchange exchange) {
    if (!org.springframework.http.HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
      return false;
    }
    return "/admin/notifications/retention/run".equals(exchange.getRequest().getPath().value());
  }

  private boolean isAdminNotificationLegalHoldReadEndpoint(ServerWebExchange exchange) {
    return org.springframework.http.HttpMethod.GET.equals(exchange.getRequest().getMethod())
        && "/admin/notifications/legal-holds".equals(exchange.getRequest().getPath().value());
  }

  /** POST create or POST .../release — same bucket as other admin writes. */
  private boolean isAdminNotificationLegalHoldWriteEndpoint(ServerWebExchange exchange) {
    if (!org.springframework.http.HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
      return false;
    }
    String path = exchange.getRequest().getPath().value();
    return "/admin/notifications/legal-holds".equals(path)
        || (path.startsWith("/admin/notifications/legal-holds/") && path.endsWith("/release"));
  }

  private boolean isAdminAuditExportEndpoint(ServerWebExchange exchange) {
    return "/admin/audit-events/export".equals(exchange.getRequest().getPath().value());
  }

  private boolean isScimEndpoint(ServerWebExchange exchange) {
    return exchange.getRequest().getPath().value().startsWith("/scim/v2/");
  }

  private Mono<String> userId(ServerWebExchange exchange) {
    return exchange
        .getPrincipal()
        .cast(JwtAuthenticationToken.class)
        .map(authentication -> authentication.getToken().getSubject())
        .defaultIfEmpty(clientIp(exchange));
  }

  private String clientIp(ServerWebExchange exchange) {
    InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
    if (remoteAddress == null || remoteAddress.getAddress() == null) {
      return "unknown";
    }
    return remoteAddress.getAddress().getHostAddress();
  }
}
