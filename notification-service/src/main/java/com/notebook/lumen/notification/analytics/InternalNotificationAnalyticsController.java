package com.notebook.lumen.notification.analytics;

import com.notebook.lumen.notification.shared.security.InternalNotificationAuthorizer;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/admin/notifications/analytics")
public class InternalNotificationAnalyticsController {

  private final InternalNotificationAnalyticsAdminProperties adminProperties;
  private final InternalNotificationAuthorizer authorizer;
  private final NotificationAnalyticsProperties analyticsProperties;
  private final NotificationAnalyticsSummaryService summaryService;

  public InternalNotificationAnalyticsController(
      InternalNotificationAnalyticsAdminProperties adminProperties,
      InternalNotificationAuthorizer authorizer,
      NotificationAnalyticsProperties analyticsProperties,
      NotificationAnalyticsSummaryService summaryService) {
    this.adminProperties = adminProperties;
    this.authorizer = authorizer;
    this.analyticsProperties = analyticsProperties;
    this.summaryService = summaryService;
  }

  @GetMapping(path = "/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  public NotificationAnalyticsSummaryResponse summary(
      @RequestHeader(value = InternalNotificationAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(name = "bucket", required = false) String ignoredBucket) {
    if (!adminProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Internal notification analytics is disabled");
    }
    if (!analyticsProperties.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification analytics is disabled");
    }
    authorizer.authorize(
        serviceAuthorization, InternalNotificationAuthorizer.ADMIN_NOTIFICATIONS_ANALYTICS_SCOPE);
    summaryService.validateRange(from, to);
    return summaryService.build(from, to);
  }
}
