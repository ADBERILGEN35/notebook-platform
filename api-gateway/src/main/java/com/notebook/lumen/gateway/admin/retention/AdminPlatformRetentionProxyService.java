package com.notebook.lumen.gateway.admin.retention;

import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.error.ErrorCode;
import com.notebook.lumen.gateway.error.ErrorResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Service
public class AdminPlatformRetentionProxyService {
  static final String READ_SCOPE = "internal:admin:retention:platform:read";
  static final String WRITE_SCOPE = "internal:admin:retention:legal-hold:write";
  static final String ACTOR_HEADER = "X-Admin-Actor-User-Id";
  private static final String IDENTITY_AUDIENCE = "identity-service";
  private static final String BASE_PATH = "/internal/admin/retention/platform";

  static final String WARN_CONTENT_UNAVAILABLE = "CONTENT_RETENTION_SERVICE_UNAVAILABLE";
  static final String WARN_CONTENT_INCLUDED = "PLATFORM_RETENTION_CONTENT_PLAN_INCLUDED";
  static final String WARN_NOTIFICATION_UNAVAILABLE = "NOTIFICATION_RETENTION_SERVICE_UNAVAILABLE";
  static final String WARN_NOTIFICATION_INCLUDED = "PLATFORM_RETENTION_NOTIFICATION_PLAN_INCLUDED";

  private final GatewayAuditProxyProperties auditProxyProperties;
  private final ServiceJwtSigner serviceJwtSigner;
  private final WebClient webClient;
  private final ContentRetentionClient contentRetentionClient;
  private final NotificationRetentionClient notificationRetentionClient;

  public AdminPlatformRetentionProxyService(
      GatewayAuditProxyProperties auditProxyProperties,
      ServiceJwtSigner serviceJwtSigner,
      WebClient.Builder webClientBuilder,
      ContentRetentionClient contentRetentionClient,
      NotificationRetentionClient notificationRetentionClient) {
    this.auditProxyProperties = auditProxyProperties;
    this.serviceJwtSigner = serviceJwtSigner;
    this.webClient = webClientBuilder.build();
    this.contentRetentionClient = contentRetentionClient;
    this.notificationRetentionClient = notificationRetentionClient;
  }

  public Mono<ResponseEntity<Object>> targets(String requestId, String path) {
    return get(baseUrl() + "/targets", READ_SCOPE, requestId, path);
  }

  public Mono<ResponseEntity<Object>> plan(
      String target, boolean dryRun, String requestId, String path) {
    UriComponentsBuilder ub =
        UriComponentsBuilder.fromUriString(baseUrl() + "/plan").queryParam("dryRun", dryRun);
    if (target != null && !target.isBlank()) {
      ub.queryParam("target", target.trim());
    }
    String planUrl = ub.build(true).toUriString();
    String holdsUrl = baseUrl() + "/legal-holds?status=ACTIVE";

    boolean holdsNeeded = contentRetentionClient.enabled() || notificationRetentionClient.enabled();
    Mono<Map<String, Object>> planMono = getMap(planUrl, READ_SCOPE, requestId);
    Mono<Map<String, Object>> holdsMono =
        holdsNeeded
            ? getMap(holdsUrl, READ_SCOPE, requestId).onErrorReturn(Map.of())
            : Mono.just(Map.of());

    return Mono.zip(planMono, holdsMono)
        .flatMap(
            tuple -> {
              Map<String, Object> plan = mutableMap(tuple.getT1());
              Set<String> holdScopes = extractHoldScopes(tuple.getT2());
              return mergeContent(plan, holdScopes, requestId)
                  .flatMap(p -> mergeNotification(p, holdScopes, requestId));
            })
        .map(AdminPlatformRetentionProxyService::planToOk)
        .onErrorResume(e -> Mono.just(mapException(e, path, requestId)));
  }

  private Mono<Map<String, Object>> mergeContent(
      Map<String, Object> plan, Set<String> holdScopes, String requestId) {
    if (!contentRetentionClient.enabled()) {
      return Mono.just(plan);
    }
    return contentRetentionClient
        .fetchPlan(holdScopes, requestId)
        .map(
            contentBody -> {
              mergeContentPlan(plan, contentBody);
              addWarning(plan, WARN_CONTENT_INCLUDED);
              return plan;
            })
        .onErrorResume(
            e -> {
              addWarning(plan, WARN_CONTENT_UNAVAILABLE);
              return Mono.just(plan);
            });
  }

  private Mono<Map<String, Object>> mergeNotification(
      Map<String, Object> plan, Set<String> holdScopes, String requestId) {
    if (!notificationRetentionClient.enabled()) {
      return Mono.just(plan);
    }
    return notificationRetentionClient
        .fetchPlan(holdScopes, requestId)
        .map(
            notificationBody -> {
              mergeNotificationPlan(plan, notificationBody);
              addWarning(plan, WARN_NOTIFICATION_INCLUDED);
              return plan;
            })
        .onErrorResume(
            e -> {
              addWarning(plan, WARN_NOTIFICATION_UNAVAILABLE);
              return Mono.just(plan);
            });
  }

  private Mono<Map<String, Object>> getMap(String url, String scope, String requestId) {
    return Mono.defer(
        () -> {
          String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, scope);
          } catch (RuntimeException e) {
            return Mono.error(e);
          }
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt);
          if (requestId != null && !requestId.isBlank()) {
            spec = spec.header("X-Request-Id", requestId);
          }
          return spec.retrieve().bodyToMono(MAP_TYPE);
        });
  }

  @SuppressWarnings("unchecked")
  static Set<String> extractHoldScopes(Map<String, Object> holdsBody) {
    Set<String> scopes = new TreeSet<>();
    if (holdsBody == null) return scopes;
    Object items = holdsBody.get("items");
    if (!(items instanceof List<?> rows)) return scopes;
    for (Object row : rows) {
      if (!(row instanceof Map<?, ?> map)) continue;
      Object scope = map.get("scope");
      if (scope == null) continue;
      Object status = map.get("status");
      if (status != null && !"ACTIVE".equals(status.toString())) continue;
      scopes.add(scope.toString());
    }
    return scopes;
  }

  @SuppressWarnings("unchecked")
  static void mergeContentPlan(Map<String, Object> plan, Map<String, Object> contentBody) {
    if (plan == null || contentBody == null) return;
    Object planTargetsObj = plan.get("targets");
    Object contentTargetsObj = contentBody.get("targets");
    if (!(planTargetsObj instanceof List<?> planTargets)
        || !(contentTargetsObj instanceof List<?> contentTargets)) {
      return;
    }
    Map<String, Map<String, Object>> contentByKey = new LinkedHashMap<>();
    for (Object t : contentTargets) {
      if (t instanceof Map<?, ?> row) {
        Object key = row.get("targetKey");
        if (key != null) {
          contentByKey.put(key.toString(), (Map<String, Object>) row);
        }
      }
    }
    List<Object> mergedTargets = new ArrayList<>();
    for (Object t : planTargets) {
      if (!(t instanceof Map<?, ?> row)) {
        mergedTargets.add(t);
        continue;
      }
      Map<String, Object> mutable = mutableMap((Map<String, Object>) row);
      Object key = mutable.get("targetKey");
      if (key != null) {
        Map<String, Object> contentRow = contentByKey.get(key.toString());
        if (contentRow != null) {
          if (contentRow.containsKey("eligibleCount")) {
            mutable.put("eligibleCount", contentRow.get("eligibleCount"));
          }
          if (contentRow.containsKey("purgeableCount")) {
            mutable.put("purgeableCount", contentRow.get("purgeableCount"));
          }
          if (contentRow.containsKey("blockedByLegalHold")) {
            mutable.put("blockedByLegalHold", contentRow.get("blockedByLegalHold"));
          }
          if (contentRow.containsKey("status")) {
            mutable.put("status", contentRow.get("status"));
          }
          mergeWarnings(mutable, contentRow.get("warnings"));
        }
      }
      mergedTargets.add(mutable);
    }
    plan.put("targets", mergedTargets);
  }

  @SuppressWarnings("unchecked")
  static void mergeNotificationPlan(
      Map<String, Object> plan, Map<String, Object> notificationBody) {
    if (plan == null || notificationBody == null) return;
    Object planTargetsObj = plan.get("targets");
    Object notificationTargetsObj = notificationBody.get("targets");
    if (!(planTargetsObj instanceof List<?> planTargets)
        || !(notificationTargetsObj instanceof List<?> notificationTargets)) {
      return;
    }
    Map<String, Map<String, Object>> notificationByKey = new LinkedHashMap<>();
    for (Object t : notificationTargets) {
      if (t instanceof Map<?, ?> row) {
        Object key = row.get("targetKey");
        if (key != null) {
          notificationByKey.put(key.toString(), (Map<String, Object>) row);
        }
      }
    }
    List<Object> mergedTargets = new ArrayList<>();
    for (Object t : planTargets) {
      if (!(t instanceof Map<?, ?> row)) {
        mergedTargets.add(t);
        continue;
      }
      Map<String, Object> mutable = mutableMap((Map<String, Object>) row);
      Object key = mutable.get("targetKey");
      if (key != null) {
        Map<String, Object> notificationRow = notificationByKey.get(key.toString());
        if (notificationRow != null) {
          for (String field :
              List.of(
                  "eligibleCount",
                  "purgeableCount",
                  "blockedByLegalHold",
                  "status",
                  "defaultRetentionDays",
                  "cutoff")) {
            if (notificationRow.containsKey(field)) {
              mutable.put(field, notificationRow.get(field));
            }
          }
          mergeWarnings(mutable, notificationRow.get("warnings"));
        }
      }
      mergedTargets.add(mutable);
    }
    plan.put("targets", mergedTargets);
  }

  @SuppressWarnings("unchecked")
  private static void mergeWarnings(Map<String, Object> target, Object extra) {
    if (!(extra instanceof List<?> extraWarnings) || extraWarnings.isEmpty()) return;
    Object existing = target.get("warnings");
    List<Object> merged = new ArrayList<>();
    if (existing instanceof List<?> existingList) {
      merged.addAll(existingList);
    }
    for (Object w : extraWarnings) {
      if (!merged.contains(w)) merged.add(w);
    }
    target.put("warnings", merged);
  }

  @SuppressWarnings("unchecked")
  static void addWarning(Map<String, Object> plan, String warning) {
    if (plan == null || warning == null) return;
    Object existing = plan.get("warnings");
    List<Object> merged = new ArrayList<>();
    if (existing instanceof List<?> list) {
      merged.addAll(list);
    }
    if (!merged.contains(warning)) merged.add(warning);
    plan.put("warnings", merged);
  }

  private static Map<String, Object> mutableMap(Map<String, Object> source) {
    return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
  }

  private static ResponseEntity<Object> planToOk(Map<String, Object> plan) {
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(plan);
  }

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  public Mono<ResponseEntity<Object>> legalHolds(String status, String requestId, String path) {
    UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(baseUrl() + "/legal-holds");
    if (status != null && !status.isBlank()) {
      ub.queryParam("status", status.trim());
    }
    return get(ub.build(true).toUriString(), READ_SCOPE, requestId, path);
  }

  public Mono<ResponseEntity<Object>> createLegalHold(
      Map<String, Object> body, String actorUserId, String requestId, String path) {
    return post(baseUrl() + "/legal-holds", body, actorUserId, requestId, path);
  }

  public Mono<ResponseEntity<Object>> releaseLegalHold(
      String id, Map<String, Object> body, String actorUserId, String requestId, String path) {
    return post(baseUrl() + "/legal-holds/" + id + "/release", body, actorUserId, requestId, path);
  }

  private Mono<ResponseEntity<Object>> get(
      String url, String scope, String requestId, String gatewayPath) {
    return Mono.defer(
        () -> {
          String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, scope);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          WebClient.RequestHeadersSpec<?> spec =
              webClient
                  .get()
                  .uri(url)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt);
          if (requestId != null && !requestId.isBlank())
            spec = spec.header("X-Request-Id", requestId);
          return spec.retrieve()
              .bodyToMono(Object.class)
              .map(body -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  private Mono<ResponseEntity<Object>> post(
      String url,
      Map<String, Object> body,
      String actorUserId,
      String requestId,
      String gatewayPath) {
    return Mono.defer(
        () -> {
          String jwt;
          try {
            jwt = serviceJwtSigner.sign(IDENTITY_AUDIENCE, WRITE_SCOPE);
          } catch (RuntimeException e) {
            return Mono.just(jwtFailure(requestId, gatewayPath));
          }
          WebClient.RequestBodySpec spec =
              webClient
                  .post()
                  .uri(url)
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
                  .header(ACTOR_HEADER, actorUserId);
          if (requestId != null && !requestId.isBlank())
            spec = spec.header("X-Request-Id", requestId);
          return spec.bodyValue(body == null ? Map.of() : body)
              .retrieve()
              .bodyToMono(Object.class)
              .map(
                  response ->
                      ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response))
              .onErrorResume(e -> Mono.just(mapException(e, gatewayPath, requestId)));
        });
  }

  private ResponseEntity<Object> jwtFailure(String requestId, String gatewayPath) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                Instant.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                ErrorCode.AUDIT_PROXY_INTERNAL_AUTH_FAILED.name(),
                "Service JWT signing failed for platform retention proxy",
                gatewayPath,
                requestId,
                null));
  }

  private static ResponseEntity<Object> mapException(
      Throwable e, String gatewayPath, String requestId) {
    if (e instanceof WebClientResponseException w) {
      return ResponseEntity.status(w.getStatusCode())
          .contentType(MediaType.APPLICATION_JSON)
          .body(w.getResponseBodyAsString());
    }
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new ErrorResponse(
                Instant.now(),
                HttpStatus.BAD_GATEWAY.value(),
                ErrorCode.INTERNAL_GATEWAY_ERROR.name(),
                "identity-service unavailable",
                gatewayPath,
                requestId,
                null));
  }

  private String baseUrl() {
    String base = auditProxyProperties.identityServiceUrl();
    if (base == null || base.isBlank()) base = "http://identity-service:8081";
    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
    return base + BASE_PATH;
  }
}
