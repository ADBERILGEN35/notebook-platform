package com.notebook.lumen.identity.admin.rbac.overrides;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class AdminRbacOverrideManifestParser {

  private static final Set<String> SUPPORTED_STATUSES =
      Set.of("APPROVED_FOR_APPLY", "DISABLED", "APPLIED", "REVOKED", "EXPIRED");

  private final UserRepository userRepository;

  public AdminRbacOverrideManifestParser(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public AdminRbacOverrideParseResult parse(
      String yamlText, AdminRbacOverridesProperties props, boolean checkUserExists) {
    List<String> warnings = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    List<AdminRbacOverrideAssignmentRow> accepted = new ArrayList<>();

    if (yamlText == null || yamlText.isBlank()) {
      warnings.add("OVERRIDE_EMPTY_DOCUMENT");
      return new AdminRbacOverrideParseResult(accepted, warnings, errors, 0, 0, "");
    }

    final Yaml yaml = new Yaml();
    final Object rootObj;
    try {
      rootObj = yaml.load(yamlText);
    } catch (RuntimeException e) {
      errors.add("YAML_PARSE_ERROR");
      return new AdminRbacOverrideParseResult(List.of(), List.of(), errors, 0, 1, "");
    }
    if (!(rootObj instanceof Map<?, ?> root)) {
      errors.add("OVERRIDE_ROOT_NOT_MAP");
      return new AdminRbacOverrideParseResult(List.of(), warnings, errors, 0, 1, "");
    }
    Object ar = root.get("adminRbacOverrides");
    if (!(ar instanceof Map<?, ?> arMap)) {
      errors.add("OVERRIDE_MISSING_ADMIN_RBAC_OVERRIDES");
      return new AdminRbacOverrideParseResult(List.of(), warnings, errors, 0, 1, "");
    }
    Object ver = arMap.get("version");
    String manifestVersion = ver == null ? "" : String.valueOf(ver).trim();
    if (ver == null || !"1".equals(String.valueOf(ver).trim())) {
      warnings.add("OVERRIDE_UNSUPPORTED_VERSION");
    }
    Object al = arMap.get("assignments");
    if (!(al instanceof List<?> rawList)) {
      warnings.add("OVERRIDE_ASSIGNMENTS_NOT_LIST");
      return new AdminRbacOverrideParseResult(List.of(), warnings, errors, 0, 0, manifestVersion);
    }

    int rawCount = rawList.size();
    if (rawCount > props.maxAssignments()) {
      warnings.add("OVERRIDE_LIMIT_EXCEEDED");
      return new AdminRbacOverrideParseResult(List.of(), warnings, errors, 0, rawCount, manifestVersion);
    }

    int ignored = 0;
    for (Object row : rawList) {
      if (!(row instanceof Map<?, ?> m)) {
        ignored++;
        warnings.add("OVERRIDE_INVALID_ROW_NOT_MAP");
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) new LinkedHashMap<>(toStringKeyedMap(m));
      Optional<AdminRbacOverrideAssignmentRow> parsed =
          parseRow(map, props, warnings, errors, checkUserExists);
      if (parsed.isPresent()) {
        accepted.add(parsed.get());
      } else {
        ignored++;
      }
    }

    dedupeWarnings(warnings);
    return new AdminRbacOverrideParseResult(
        accepted, warnings, errors, accepted.size(), ignored, manifestVersion);
  }

  private static Map<String, Object> toStringKeyedMap(Map<?, ?> in) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<?, ?> e : in.entrySet()) {
      out.put(String.valueOf(e.getKey()), e.getValue());
    }
    return out;
  }

  private Optional<AdminRbacOverrideAssignmentRow> parseRow(
      Map<String, Object> m,
      AdminRbacOverridesProperties props,
      List<String> warnings,
      List<String> errors,
      boolean checkUserExists) {

    String status = upper(m.get("status"));
    if (status == null || status.isBlank()) {
      warnings.add("OVERRIDE_INVALID_IGNORED");
      return Optional.empty();
    }
    if ("DISABLED".equals(status)) {
      return Optional.empty();
    }
    if ("APPLIED".equals(status) || "REVOKED".equals(status) || "EXPIRED".equals(status)) {
      warnings.add("OVERRIDE_FUTURE_STATUS_IGNORED");
      return Optional.empty();
    }
    if (!"APPROVED_FOR_APPLY".equals(status)) {
      if (!SUPPORTED_STATUSES.contains(status)) {
        warnings.add("OVERRIDE_UNKNOWN_STATUS_IGNORED");
      } else {
        warnings.add("OVERRIDE_NON_APPROVED_STATUS_IGNORED");
      }
      return Optional.empty();
    }

    UUID userId;
    try {
      userId = UUID.fromString(String.valueOf(m.get("userId")).trim());
    } catch (Exception e) {
      warnings.add("OVERRIDE_INVALID_USER_ID");
      return Optional.empty();
    }

    if (checkUserExists && !userRepository.existsById(userId)) {
      warnings.add("OVERRIDE_INVALID_IGNORED");
      return Optional.empty();
    }

    String role = upper(m.get("role"));
    if (role == null || !PlatformAdminRbacConstants.assignableAdminRoles().contains(role)) {
      warnings.add("OVERRIDE_UNKNOWN_ROLE_IGNORED");
      return Optional.empty();
    }

    String action = upper(m.get("action"));
    if (!"GRANT".equals(action) && !"REVOKE".equals(action)) {
      warnings.add("OVERRIDE_INVALID_ACTION_IGNORED");
      return Optional.empty();
    }

    Instant expiresAt = null;
    Object exp = m.get("expiresAt");
    if (exp != null
        && !String.valueOf(exp).equalsIgnoreCase("null")
        && !String.valueOf(exp).isBlank()) {
      try {
        expiresAt = Instant.parse(String.valueOf(exp).trim());
      } catch (DateTimeParseException e) {
        warnings.add("OVERRIDE_INVALID_IGNORED");
        return Optional.empty();
      }
      if (expiresAt.isBefore(Instant.now())) {
        warnings.add("OVERRIDE_EXPIRED");
        return Optional.empty();
      }
    }

    String reasonRef = m.get("reasonRef") == null ? "" : String.valueOf(m.get("reasonRef")).trim();
    if (reasonRef.isBlank()) {
      warnings.add("OVERRIDE_INVALID_IGNORED");
      return Optional.empty();
    }

    UUID requestedBy = parseUuidLenient(m.get("requestedBy"));
    UUID approvedBy = parseUuidLenient(m.get("approvedBy"));

    Instant createdAt = Instant.EPOCH;
    Object ca = m.get("createdAt");
    if (ca != null && !String.valueOf(ca).isBlank()) {
      try {
        createdAt = Instant.parse(String.valueOf(ca).trim());
      } catch (DateTimeParseException ignored) {
        createdAt = Instant.EPOCH;
      }
    }

    String stableId = m.get("id") == null ? null : String.valueOf(m.get("id")).trim();
    if (stableId != null && stableId.isBlank()) {
      stableId = null;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> meta =
        m.get("metadata") instanceof Map ? (Map<String, Object>) m.get("metadata") : Map.of();
    String metaSource = AdminRbacOverrideAssignmentRow.metadataSourceFrom(meta);

    return Optional.of(
        new AdminRbacOverrideAssignmentRow(
            stableId,
            userId,
            role,
            action,
            reasonRef,
            requestedBy,
            approvedBy,
            status,
            expiresAt,
            createdAt,
            metaSource));
  }

  private static UUID parseUuidLenient(Object raw) {
    if (raw == null) {
      return null;
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty()) {
      return null;
    }
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String upper(Object o) {
    if (o == null) {
      return "";
    }
    return String.valueOf(o).trim().toUpperCase(Locale.ROOT);
  }

  private static void dedupeWarnings(List<String> warnings) {
    List<String> distinct = warnings.stream().distinct().toList();
    warnings.clear();
    warnings.addAll(distinct);
  }
}
