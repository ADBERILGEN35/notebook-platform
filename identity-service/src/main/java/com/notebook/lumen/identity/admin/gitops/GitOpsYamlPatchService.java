package com.notebook.lumen.identity.admin.gitops;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
      String diffPreview) {}

  public PatchPlan buildPatchPlan(String operationType, String normalizedRequestedValue, String environment) {
    GitOpsPathMapping mapping =
        GitOpsPathMapping.forOperation(operationType)
            .orElseThrow(
                () ->
                    new AdminGitOpsException(
                        "ADMIN_GITOPS_OPERATION_UNSUPPORTED",
                        HttpStatus.BAD_REQUEST,
                        "GitOps mapping not defined for operation: " + operationType));

    String rel = GitOpsPathMapping.relativeValuesFile(environment);
    Map<String, Object> root = loadBaselineMap(environment);
    List<String> segments = List.of("config", mapping.configKey());
    Object oldRaw = getAtPath(root, segments);
    String oldStr = oldRaw == null ? "" : String.valueOf(oldRaw);

    Map<String, Object> copy = deepCopy(root);
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
        unifiedDiffPreview(before, after));
  }

  /** Apply patch to arbitrary YAML text (e.g. from GitHub). Returns new content or throws. */
  public String applyPatchToContent(String yamlContent, String operationType, String normalizedRequestedValue) {
    GitOpsPathMapping mapping =
        GitOpsPathMapping.forOperation(operationType)
            .orElseThrow(
                () ->
                    new AdminGitOpsException(
                        "ADMIN_GITOPS_OPERATION_UNSUPPORTED",
                        HttpStatus.BAD_REQUEST,
                        "GitOps mapping not defined for operation: " + operationType));
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

  @SuppressWarnings("unchecked")
  private static Map<String, Object> loadBaselineMap(String environment) {
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

  private static Yaml yaml() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    return new Yaml(options);
  }

  private static String dump(Map<String, Object> root) {
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
  private static Map<String, Object> deepCopy(Map<String, Object> in) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (var e : in.entrySet()) {
      if (e.getValue() instanceof Map) {
        out.put(e.getKey(), deepCopy((Map<String, Object>) e.getValue()));
      } else {
        out.put(e.getKey(), e.getValue());
      }
    }
    return out;
  }

  private static String unifiedDiffPreview(String before, String after) {
    List<String> a = List.of(before.split("\n"));
    List<String> b = List.of(after.split("\n"));
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
