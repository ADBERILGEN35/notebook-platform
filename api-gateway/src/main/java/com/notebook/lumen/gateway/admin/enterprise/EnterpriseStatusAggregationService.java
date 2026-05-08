package com.notebook.lumen.gateway.admin.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.common.security.servicejwt.ServiceJwtSigner;
import com.notebook.lumen.gateway.admin.audit.AuditProxyService;
import com.notebook.lumen.gateway.config.GatewayAdminEnterpriseProperties;
import com.notebook.lumen.gateway.config.GatewayAdminProperties;
import com.notebook.lumen.gateway.config.GatewayAuditExportProperties;
import com.notebook.lumen.gateway.config.GatewayAuditProxyProperties;
import com.notebook.lumen.gateway.config.GatewayAuthProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
public class EnterpriseStatusAggregationService {
  private static final Logger log = LoggerFactory.getLogger(EnterpriseStatusAggregationService.class);
  static final String STATUS_SCOPE = "internal:admin:status:read";
  private static final String IDENTITY_AUDIENCE = "identity-service";
  private static final String NOTIFICATION_AUDIENCE = "notification-service";

  private final ObjectMapper objectMapper;
  private final WebClient webClient;
  private final ServiceJwtSigner auditServiceJwtSigner;
  private final GatewayAuditProxyProperties auditProxyProperties;
  private final GatewayAdminEnterpriseProperties enterpriseProperties;
  private final GatewayAdminProperties adminProperties;
  private final GatewayAuditExportProperties auditExportProperties;
  private final GatewayAuthProperties authProperties;
  private final Environment environment;
  private final EnterpriseStatusWarningEngine warningEngine;

  public EnterpriseStatusAggregationService(
      ObjectMapper objectMapper,
      WebClient.Builder webClientBuilder,
      ServiceJwtSigner auditServiceJwtSigner,
      GatewayAuditProxyProperties auditProxyProperties,
      GatewayAdminEnterpriseProperties enterpriseProperties,
      GatewayAdminProperties adminProperties,
      GatewayAuditExportProperties auditExportProperties,
      GatewayAuthProperties authProperties,
      Environment environment,
      EnterpriseStatusWarningEngine warningEngine) {
    this.objectMapper = objectMapper;
    this.webClient = webClientBuilder.build();
    this.auditServiceJwtSigner = auditServiceJwtSigner;
    this.auditProxyProperties = auditProxyProperties;
    this.enterpriseProperties = enterpriseProperties;
    this.adminProperties = adminProperties;
    this.auditExportProperties = auditExportProperties;
    this.authProperties = authProperties;
    this.environment = environment;
    this.warningEngine = warningEngine;
  }

  public Mono<EnterpriseStatusResponse> loadStatus() {
    Mono<Optional<JsonNode>> identityMono = fetchIdentityStatus();
    Mono<Optional<JsonNode>> notificationMono = fetchNotificationStatus();
    return Mono.zip(identityMono, notificationMono)
        .map(
            tuple -> {
              Optional<JsonNode> identity = tuple.getT1();
              Optional<JsonNode> notification = tuple.getT2();
              boolean identityUnavailable = identity.isEmpty();
              boolean notificationUnavailable = notification.isEmpty();
              EnterpriseStatusFeatures features =
                  mergeFeatures(identity.orElse(null), notification.orElse(null));
              List<EnterpriseWarning> warnings =
                  warningEngine.build(features, identityUnavailable, notificationUnavailable);
              return new EnterpriseStatusResponse(
                  resolveEnvironment(),
                  Instant.now(),
                  features,
                  warnings,
                  identityUnavailable,
                  notificationUnavailable);
            });
  }

  private String resolveEnvironment() {
    String[] profiles = environment.getActiveProfiles();
    if (profiles.length == 0) {
      return "default";
    }
    return String.join(",", profiles);
  }

  private Mono<Optional<JsonNode>> fetchIdentityStatus() {
    String base = auditProxyProperties.identityServiceUrl();
    if (base == null || base.isBlank()) {
      return Mono.just(Optional.empty());
    }
    String jwt;
    try {
      jwt = auditServiceJwtSigner.sign(IDENTITY_AUDIENCE, STATUS_SCOPE);
    } catch (RuntimeException e) {
      log.warn("enterprise_status_identity_jwt_failed message={}", e.getMessage());
      return Mono.just(Optional.empty());
    }
    String uri = trimTrailingSlash(base) + enterpriseProperties.effectiveIdentityStatusPath();
    return webClient
        .get()
        .uri(uri)
        .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(3))
        .map(body -> parseJson(body))
        .onErrorResume(
            e -> {
              log.warn(
                  "enterprise_status_identity_failed message={}",
                  e instanceof WebClientResponseException w
                      ? w.getStatusCode().value() + " " + w.getResponseBodyAsString()
                      : e.getMessage());
              return Mono.just(Optional.empty());
            });
  }

  private Mono<Optional<JsonNode>> fetchNotificationStatus() {
    String base = enterpriseProperties.effectiveNotificationServiceUrl();
    if (base == null || base.isBlank()) {
      return Mono.just(Optional.empty());
    }
    String jwt;
    try {
      jwt = auditServiceJwtSigner.sign(NOTIFICATION_AUDIENCE, STATUS_SCOPE);
    } catch (RuntimeException e) {
      log.warn("enterprise_status_notification_jwt_failed message={}", e.getMessage());
      return Mono.just(Optional.empty());
    }
    String uri = trimTrailingSlash(base) + enterpriseProperties.effectiveNotificationStatusPath();
    return webClient
        .get()
        .uri(uri)
        .header(AuditProxyService.INTERNAL_AUTH_HEADER, "Bearer " + jwt)
        .retrieve()
        .bodyToMono(String.class)
        .timeout(Duration.ofSeconds(3))
        .map(this::parseJson)
        .onErrorResume(
            e -> {
              log.warn(
                  "enterprise_status_notification_failed message={}",
                  e instanceof WebClientResponseException w
                      ? w.getStatusCode().value() + " " + w.getResponseBodyAsString()
                      : e.getMessage());
              return Mono.just(Optional.empty());
            });
  }

  private Optional<JsonNode> parseJson(String body) {
    try {
      return Optional.of(objectMapper.readTree(body));
    } catch (Exception e) {
      log.warn("enterprise_status_parse_failed message={}", e.getMessage());
      return Optional.empty();
    }
  }

  private EnterpriseStatusFeatures mergeFeatures(JsonNode identity, JsonNode notification) {
    SsoStatus sso = mapSso(identity);
    ScimStatus scim = mapScim(identity);
    MfaStatus mfa = mapMfa(identity);
    SiemStatus siem = mapSiem(identity);
    AuditExportStatus auditExport = mapAuditExport();
    NotificationsStatus notifications = mapNotifications(notification);
    GatewaySecurityStatus gatewaySecurity = mapGatewaySecurity();
    return new EnterpriseStatusFeatures(sso, scim, mfa, siem, auditExport, notifications, gatewaySecurity);
  }

  private SsoStatus mapSso(JsonNode identity) {
    if (identity == null) {
      return new SsoStatus(false, 0, false, false, false);
    }
    JsonNode n = identity.path("sso");
    return new SsoStatus(
        n.path("enabled").asBoolean(false),
        n.path("providersConfigured").asInt(0),
        n.path("allowedDomainsConfigured").asBoolean(false),
        n.path("adminGroupMappingConfigured").asBoolean(false),
        n.path("trustIdpMfa").asBoolean(false));
  }

  private ScimStatus mapScim(JsonNode identity) {
    if (identity == null) {
      return new ScimStatus(false, false, false, false);
    }
    JsonNode n = identity.path("scim");
    return new ScimStatus(
        n.path("enabled").asBoolean(false),
        n.path("groupsEnabled").asBoolean(false),
        n.path("adminGroupsConfigured").asBoolean(false),
        n.path("tokenConfigured").asBoolean(false));
  }

  private MfaStatus mapMfa(JsonNode identity) {
    JsonNode idMfa = identity == null ? null : identity.path("mfa");
    boolean identityMfa = idMfa != null && idMfa.path("enabled").asBoolean(false);
    boolean webauthn = idMfa != null && idMfa.path("webauthnEnabled").asBoolean(false);
    String mode = adminProperties.effectiveMfaMode();
    List<String> methods = new ArrayList<>(adminProperties.acceptedMfaMethods());
    return new MfaStatus(mode, methods, identityMfa, webauthn);
  }

  private SiemStatus mapSiem(JsonNode identity) {
    if (identity == null) {
      return new SiemStatus(false, "noop", false, false, false);
    }
    JsonNode n = identity.path("siem");
    String provider = n.path("provider").asText("noop");
    return new SiemStatus(
        n.path("enabled").asBoolean(false),
        provider,
        n.path("workerEnabled").asBoolean(false),
        n.path("endpointConfigured").asBoolean(false),
        n.path("secretConfigured").asBoolean(false));
  }

  private AuditExportStatus mapAuditExport() {
    var machine = auditExportProperties.effectiveMachineAuth();
    boolean pk =
        (machine.publicKey() != null && !machine.publicKey().isBlank())
            || (machine.publicKeyPath() != null && !machine.publicKeyPath().isBlank());
    return new AuditExportStatus(
        auditExportProperties.enabled(),
        machine.enabled(),
        auditExportProperties.scheduledExportConfigured(),
        auditExportProperties.archiveUploadEnabled(),
        auditExportProperties.effectiveArchiveProvider(),
        pk);
  }

  private NotificationsStatus mapNotifications(JsonNode notification) {
    if (notification == null) {
      return new NotificationsStatus(false, false, false, false);
    }
    JsonNode sse = notification.path("sse");
    JsonNode digest = notification.path("digest");
    return new NotificationsStatus(
        sse.path("enabled").asBoolean(false),
        sse.path("distributedFanoutEnabled").asBoolean(false),
        digest.path("enabled").asBoolean(false),
        digest.path("workerEnabled").asBoolean(false));
  }

  private GatewaySecurityStatus mapGatewaySecurity() {
    boolean cookie = authProperties.cookieTransportEnabled();
    return new GatewaySecurityStatus(
        adminProperties.enabled(),
        adminProperties.effectiveAudit().enabled(),
        adminProperties.effectiveMfaMode(),
        new ArrayList<>(adminProperties.acceptedMfaMethods()),
        true,
        cookie,
        authProperties.effectiveTransport(),
        cookie);
  }

  private static String trimTrailingSlash(String base) {
    if (base.endsWith("/")) {
      return base.substring(0, base.length() - 1);
    }
    return base;
  }
}
