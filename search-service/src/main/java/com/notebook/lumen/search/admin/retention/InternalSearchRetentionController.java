package com.notebook.lumen.search.admin.retention;

import com.notebook.lumen.search.admin.retention.SearchRetentionPlanDtos.SearchRetentionPlanResponse;
import com.notebook.lumen.search.shared.exception.SearchException;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/retention/search")
public class InternalSearchRetentionController {

  private final SearchRetentionAdminAuthorizer authorizer;
  private final SearchRetentionPlanService planService;

  public InternalSearchRetentionController(
      SearchRetentionAdminAuthorizer authorizer, SearchRetentionPlanService planService) {
    this.authorizer = authorizer;
    this.planService = planService;
  }

  @GetMapping(path = "/plan", produces = MediaType.APPLICATION_JSON_VALUE)
  public SearchRetentionPlanResponse plan(
      @RequestHeader(value = SearchRetentionAdminAuthorizer.HEADER_NAME, required = false)
          String serviceAuthorization,
      @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun,
      @RequestParam(name = "target", required = false) String target,
      @RequestParam(name = "legalHoldScopes", required = false) String legalHoldScopes,
      @RequestParam(name = "generatedAt", required = false) Instant generatedAt) {
    authorizer.authorize(serviceAuthorization);
    if (!dryRun) {
      throw new SearchException(
          HttpStatus.BAD_REQUEST,
          "RETENTION_DRY_RUN_ONLY",
          "Search retention plan endpoint supports dry-run only");
    }
    Optional<SearchRetentionTargetKey> targetFilter =
        target == null || target.isBlank()
            ? Optional.empty()
            : SearchRetentionTargetKey.fromKey(target)
                .or(
                    () -> {
                      throw new SearchException(
                          HttpStatus.BAD_REQUEST,
                          "RETENTION_TARGET_UNKNOWN",
                          "Unknown target: " + target);
                    });
    Set<SearchRetentionLegalHoldScope> scopes = parseScopes(legalHoldScopes);
    return planService.buildPlan(targetFilter, scopes, Optional.ofNullable(generatedAt));
  }

  private Set<SearchRetentionLegalHoldScope> parseScopes(String raw) {
    if (raw == null || raw.isBlank()) return Set.of();
    Set<SearchRetentionLegalHoldScope> parsed = EnumSet.noneOf(SearchRetentionLegalHoldScope.class);
    for (String part : Arrays.asList(raw.split(","))) {
      String token = part.trim();
      if (token.isEmpty()) continue;
      String head = token.contains(":") ? token.substring(0, token.indexOf(':')) : token;
      SearchRetentionLegalHoldScope.fromString(head).ifPresent(parsed::add);
    }
    return parsed;
  }
}
