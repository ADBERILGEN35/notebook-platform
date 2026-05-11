package com.notebook.lumen.identity.admin.gitops;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.admin.changerequest.AdminOperationRegistry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

@Service
public class GitOpsYamlPatchService {

  public record PatchPlan(
      String relativePath,
      String yamlDotPath,
      String oldValue,
      String newValue,
      String yamlBefore,
      String yamlAfter,
      String diffPreview,
      List<String> warnings) {

    public PatchPlan(
        String relativePath,
        String yamlDotPath,
        String oldValue,
        String newValue,
        String yamlBefore,
        String yamlAfter,
        String diffPreview) {
      this(relativePath, yamlDotPath, oldValue, newValue, yamlBefore, yamlAfter, diffPreview, List.of());
    }
  }

  public PatchPlan buildPatchPlan(
      String operationType,
      String normalizedRequestedValue,
      String environment,
      GitOpsRbacPatchContext rbacContext) {
    GitOpsPathMapping mapping =
        GitOpsPathMapping.forOperation(operationType)
            .orElseThrow(
                () ->
                    new AdminGitOpsException(
                        "ADMIN_GITOPS_OPERATION_UNSUPPORTED",
                        HttpStatus.BAD_REQUEST,
                        "GitOps mapping not defined for operation: " + operationType));

    if (mapping.isAdminRbacOverridesFile()) {
      if (rbacContext == null || rbacContext.changeRequestId() == null) {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_PATCH_FAILED",
            HttpStatus.BAD_REQUEST,
            "RBAC GitOps patch requires change request context");
      }
      return buildAdminRbacOverridesPatchPlan(mapping, normalizedRequestedValue, environment, rbacContext);
    }

    String rel = mapping.relativePath(environment);
    Map<String, Object> root = loadValuesBaselineMap(environment);
    List<String> segments = List.of("config", mapping.configKey());
    Object oldRaw = getAtPath(root, segments);
    String oldStr = oldRaw == null ? "" : String.valueOf(oldRaw);

    Map<String, Object> copy = deepCopyMap(root);
    setAtPath(copy, segments, normalizedRequestedValue);

    String before = dump(root);
    String after = dump(copy);
    if (before.equals(after)) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "Patch did not change YAML content");
    }

    return new PatchPlan(
        rel,
        mapping.yamlDotPath(),
        oldStr,
        normalizedRequestedValue,
        before,
        after,
        unifiedDiffPreview(before, after),
        List.of());
  }

  private PatchPlan buildAdminRbacOverridesPatchPlan(
      GitOpsPathMapping mapping,
      String normalizedRequestedValue,
      String environment,
      GitOpsRbacPatchContext ctx) {
    RbacTriple triple = parseRbacNormalized(normalizedRequestedValue);
    if (!PlatformAdminRbacConstants.assignableAdminRoles().contains(triple.role())) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "Role is not allow-listed for GitOps RBAC proposals");
    }

    Map<String, Object> root = loadAdminRbacBaselineMap(environment);
    List<String> warnings = new ArrayList<>();
    if (hasDuplicateAssignment(root, triple.userId(), triple.role(), triple.action())) {
      warnings.add("RBAC_ASSIGNMENT_ALREADY_PROPOSED");
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> copyRoot = (Map<String, Object>) deepCopyValue(root);

    Map<String, Object> assignment =
        buildAssignmentMap(
            triple.userId(),
            triple.role(),
            triple.action(),
            ctx.changeRequestId(),
            ctx.requestedByUserId(),
            ctx.approvedByUserId());

    appendAssignment(copyRoot, assignment);

    String before = dump(root);
    String after = dump(copyRoot);
    if (before.equals(after)) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "Patch did not change YAML content");
    }

    String rel = mapping.relativePath(environment);
    String assignmentYaml = dump(assignment).trim();
    return new PatchPlan(
        rel,
        mapping.yamlDotPath(),
        "",
        assignmentYaml,
        before,
        after,
        unifiedDiffPreview(before, after),
        List.copyOf(warnings));
  }

  /** Baseline YAML for an empty admin RBAC overrides file (e.g. GitHub file missing on branch). */
  public String baselineAdminRbacOverridesYaml(String environment) {
    return dump(loadAdminRbacBaselineMap(environment));
  }

  /**
   * Apply allow-listed patch to YAML text (e.g. from GitHub). For RBAC operations {@code rbacContext} must be
   * non-null with change request id and actor ids.
   */
  public String applyPatchToContent(
      String yamlContent,
      String operationType,
      String normalizedRequestedValue,
      GitOpsRbacPatchContext rbacContext) {
    GitOpsPathMapping mapping =
        GitOpsPathMapping.forOperation(operationType)
            .orElseThrow(
                () ->
                    new AdminGitOpsException(
                        "ADMIN_GITOPS_OPERATION_UNSUPPORTED",
                        HttpStatus.BAD_REQUEST,
                        "GitOps mapping not defined for operation: " + operationType));

    if (mapping.isAdminRbacOverridesFile()) {
      if (rbacContext == null || rbacContext.changeRequestId() == null) {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "RBAC GitOps patch requires change request context");
      }
      Yaml yaml = yaml();
      Map<String, Object> root = yaml.load(yamlContent == null || yamlContent.isBlank() ? "{}" : yamlContent);
      if (root == null) {
        root = new LinkedHashMap<>();
      }
      RbacTriple triple = parseRbacNormalized(normalizedRequestedValue);
      if (!PlatformAdminRbacConstants.assignableAdminRoles().contains(triple.role())) {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "Role is not allow-listed for GitOps RBAC proposals");
      }
      Map<String, Object> assignment =
          buildAssignmentMap(
              triple.userId(),
              triple.role(),
              triple.action(),
              rbacContext.changeRequestId(),
              rbacContext.requestedByUserId(),
              rbacContext.approvedByUserId());
      appendAssignment(root, assignment);
      return dump(root);
    }

    Yaml yaml = yaml();
    Map<String, Object> root = yaml.load(yamlContent);
    if (root == null) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "Could not parse target YAML");
    }
    List<String> segments = List.of("config", mapping.configKey());
    setAtPath(root, segments, normalizedRequestedValue);
    return dump(root);
  }

  private record RbacTriple(String action, String role, String userId) {}

  private static RbacTriple parseRbacNormalized(String normalized) {
    if (normalized == null || normalized.isBlank()) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "RBAC normalized requestedValue is empty");
    }
    String[] parts = normalized.trim().split(":");
    if (parts.length != 3) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED",
          HttpStatus.BAD_REQUEST,
          "RBAC normalized value must be action:role:userId");
    }
    String action = parts[0].trim().toUpperCase(Locale.ROOT);
    String role = parts[1].trim().toUpperCase(Locale.ROOT);
    String userId = parts[2].trim();
    if (!"GRANT".equals(action) && !"REVOKE".equals(action)) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "RBAC action must be GRANT or REVOKE");
    }
    try {
      UUID.fromString(userId);
    } catch (IllegalArgumentException e) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.BAD_REQUEST, "RBAC userId must be a UUID");
    }
    return new RbacTriple(action, role, userId);
  }

  private static Map<String, Object> buildAssignmentMap(
      String userId,
      String role,
      String action,
      UUID changeRequestId,
      UUID requestedByUserId,
      UUID approvedByUserId) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", UUID.randomUUID().toString());
    m.put("userId", userId);
    m.put("role", role);
    m.put("action", action);
    m.put("reasonRef", "change-request:" + changeRequestId);
    m.put("requestedBy", requestedByUserId == null ? "" : requestedByUserId.toString());
    m.put("approvedBy", approvedByUserId == null ? "" : approvedByUserId.toString());
    m.put("expiresAt", null);
    m.put("status", "APPROVED_FOR_APPLY");
    m.put("createdAt", Instant.now().toString());
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("source", "gitops");
    m.put("metadata", meta);
    return m;
  }

  @SuppressWarnings("unchecked")
  private static void appendAssignment(Map<String, Object> root, Map<String, Object> assignment) {
    Object o = root.get("adminRbacOverrides");
    if (!(o instanceof Map)) {
      Map<String, Object> wrapper = new LinkedHashMap<>();
      wrapper.put("version", 1);
      wrapper.put("assignments", new ArrayList<Object>());
      root.put("adminRbacOverrides", wrapper);
    }
    Map<String, Object> ar = (Map<String, Object>) root.get("adminRbacOverrides");
    Object al = ar.get("assignments");
    if (!(al instanceof List)) {
      al = new ArrayList<Object>();
      ar.put("assignments", al);
    }
    List<Object> lst = (List<Object>) al;
    lst.add(assignment);
  }

  @SuppressWarnings("unchecked")
  private static boolean hasDuplicateAssignment(Map<String, Object> root, String userId, String role, String action) {
    Object o = root.get("adminRbacOverrides");
    if (!(o instanceof Map)) {
      return false;
    }
    Object al = ((Map<String, Object>) o).get("assignments");
    if (!(al instanceof List)) {
      return false;
    }
    for (Object row : (List<?>) al) {
      if (!(row instanceof Map)) {
        continue;
      }
      Map<String, Object> m = (Map<String, Object>) row;
      String u = String.valueOf(m.getOrDefault("userId", "")).trim();
      String r = String.valueOf(m.getOrDefault("role", "")).trim().toUpperCase(Locale.ROOT);
      String a = String.valueOf(m.getOrDefault("action", "")).trim().toUpperCase(Locale.ROOT);
      if (userId.equalsIgnoreCase(u) && role.equalsIgnoreCase(r) && action.equalsIgnoreCase(a)) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> loadValuesBaselineMap(String environment) {
    String env = environment.trim().toLowerCase(Locale.ROOT);
    String resource = "gitops-baselines/" + env + "/values.yaml";
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_MAPPING_NOT_FOUND",
            HttpStatus.BAD_REQUEST,
            "No baseline template for environment: " + environment);
      }
      String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Yaml yaml = yaml();
      Map<String, Object> m = yaml.load(raw);
      if (m == null) {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Invalid baseline YAML");
      }
      return m;
    } catch (AdminGitOpsException e) {
      throw e;
    } catch (Exception e) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load baseline: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> loadAdminRbacBaselineMap(String environment) {
    String env = environment.trim().toLowerCase(Locale.ROOT);
    String resource = "gitops-baselines/" + env + "/admin-rbac-overrides.yaml";
    try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_MAPPING_NOT_FOUND",
            HttpStatus.BAD_REQUEST,
            "No admin RBAC baseline for environment: " + environment);
      }
      String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      Yaml yaml = yaml();
      Map<String, Object> m = yaml.load(raw);
      if (m == null) {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Invalid admin RBAC baseline YAML");
      }
      return m;
    } catch (AdminGitOpsException e) {
      throw e;
    } catch (Exception e) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED", HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load baseline: " + e.getMessage());
    }
  }

  private static Yaml yaml() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    return new Yaml(options);
  }

  private static String dump(Object root) {
    return yaml().dump(root);
  }

  @SuppressWarnings("unchecked")
  private static Object getAtPath(Map<String, Object> root, List<String> segments) {
    Object cur = root;
    for (String seg : segments) {
      if (!(cur instanceof Map)) {
        return null;
      }
      cur = ((Map<String, Object>) cur).get(seg);
    }
    return cur;
  }

  @SuppressWarnings("unchecked")
  private static void setAtPath(Map<String, Object> root, List<String> segments, String value) {
    if (segments.isEmpty()) {
      return;
    }
    Map<String, Object> cur = root;
    for (int i = 0; i < segments.size() - 1; i++) {
      String seg = segments.get(i);
      Object next = cur.get(seg);
      if (!(next instanceof Map)) {
        next = new LinkedHashMap<String, Object>();
        cur.put(seg, next);
      }
      cur = (Map<String, Object>) next;
    }
    cur.put(segments.get(segments.size() - 1), value);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> deepCopyMap(Map<String, Object> in) {
    return (Map<String, Object>) deepCopyValue(in);
  }

  private static Object deepCopyValue(Object v) {
    if (v instanceof Map) {
      Map<?, ?> in = (Map<?, ?>) v;
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : in.entrySet()) {
        out.put(String.valueOf(e.getKey()), deepCopyValue(e.getValue()));
      }
      return out;
    }
    if (v instanceof List) {
      List<?> in = (List<?>) v;
      List<Object> out = new ArrayList<>();
      for (Object o : in) {
        out.add(deepCopyValue(o));
      }
      return out;
    }
    return v;
  }

  private static String unifiedDiffPreview(String before, String after) {
    List<String> a = List.of(before.split("\n", -1));
    List<String> b = List.of(after.split("\n", -1));
    List<String> lines = new ArrayList<>();
    lines.add("--- baseline");
    lines.add("+++ patched");
    int max = Math.max(a.size(), b.size());
    for (int i = 0; i < max; i++) {
      String la = i < a.size() ? a.get(i) : "";
      String lb = i < b.size() ? b.get(i) : "";
      if (!la.equals(lb)) {
        lines.add("-" + la);
        lines.add("+" + lb);
      }
    }
    return String.join("\n", lines);
  }
}
