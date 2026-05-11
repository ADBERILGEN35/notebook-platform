package com.notebook.lumen.identity.admin.gitops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notebook.lumen.identity.admin.changerequest.AdminOperationRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * GitHub REST foundation: read values file, commit on a new branch, open PR. Token stays
 * server-side only; never logged.
 */
@Component
public class GithubGitOpsPullRequestProvider implements GitOpsPullRequestProvider {
  private static final Logger log = LoggerFactory.getLogger(GithubGitOpsPullRequestProvider.class);

  private final RestClient restClient;
  private final ObjectMapper objectMapper;
  private final GitOpsYamlPatchService yamlPatchService;

  public GithubGitOpsPullRequestProvider(
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      GitOpsYamlPatchService yamlPatchService) {
    this.restClient = restClientBuilder.baseUrl("https://api.github.com").build();
    this.objectMapper = objectMapper;
    this.yamlPatchService = yamlPatchService;
  }

  @Override
  public String getProviderName() {
    return "github";
  }

  @Override
  public void validateRepositoryAccess(AdminGitOpsPrProperties config) {
    requireGithubConfig(config);
    try {
      restClient
          .get()
          .uri("/repos/{owner}/{repo}", config.repositoryOwner(), config.repositoryName())
          .header("Authorization", bearer(config))
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      log.warn("github_repo_validation_failed status={}", e.getStatusCode().value());
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PROVIDER_VALIDATION_FAILED",
          HttpStatus.BAD_GATEWAY,
          "GitHub repository validation failed");
    }
  }

  @Override
  public GitOpsPrProviderResult createPullRequest(
      GitOpsPrProviderRequest request, AdminGitOpsPrProperties config) {
    requireGithubConfig(config);
    String owner = request.repositoryOwner();
    String repo = request.repositoryName();
    String path = request.valuesFilePath();
    String base = request.baseBranch();
    String head = request.headBranch();

    JsonNode fileNode = fetchContents(owner, repo, path, base, config);
    String sha;
    String existingYaml;
    if (fileNode == null || !fileNode.path("sha").isTextual()) {
      if (AdminOperationRegistry.isRbacRoleOperation(request.operationType())) {
        String env = resolveTargetEnvironment(request);
        existingYaml = yamlPatchService.baselineAdminRbacOverridesYaml(env);
        sha = null;
      } else {
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_MAPPING_NOT_FOUND",
            HttpStatus.BAD_REQUEST,
            "GitHub file not found at path on base branch");
      }
    } else {
      sha = fileNode.get("sha").asText();
      existingYaml = decodeContent(fileNode.path("content"));
    }

    GitOpsRbacPatchContext rbacCtx = null;
    if (AdminOperationRegistry.isRbacRoleOperation(request.operationType())) {
      rbacCtx =
          new GitOpsRbacPatchContext(
              request.rbacChangeRequestId(),
              request.rbacRequestedByUserId(),
              request.rbacApprovedByUserId());
    }

    String newYaml;
    try {
      newYaml =
          yamlPatchService.applyPatchToContent(
              existingYaml, request.operationType(), request.normalizedRequestedValue(), rbacCtx);
    } catch (AdminGitOpsException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PATCH_FAILED",
          HttpStatus.BAD_REQUEST,
          "Could not apply allow-listed patch to repository file");
    }

    String baseSha = resolveBranchSha(owner, repo, base, config);
    String headBranch = createBranchWithRetry(owner, repo, head, baseSha, config);
    commitFileUpdate(owner, repo, path, headBranch, sha, newYaml, request.commitMessage(), config);

    JsonNode prNode =
        openPullRequest(owner, repo, headBranch, base, request.prTitle(), request.prBody(), config);
    String url = prNode.path("html_url").asText("");
    String number = prNode.path("number").asText("");
    return new GitOpsPrProviderResult(url, number, headBranch);
  }

  private static void requireGithubConfig(AdminGitOpsPrProperties config) {
    if (config.githubToken() == null || config.githubToken().isBlank()) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PROVIDER_FAILED",
          HttpStatus.SERVICE_UNAVAILABLE,
          "GitHub token is not configured");
    }
    if (config.repositoryOwner().isBlank() || config.repositoryName().isBlank()) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PROVIDER_FAILED",
          HttpStatus.BAD_REQUEST,
          "GitHub repository owner/name is not configured");
    }
  }

  private static String bearer(AdminGitOpsPrProperties config) {
    return "Bearer " + config.githubToken();
  }

  private static URI contentsUri(String owner, String repo, String filePath, String ref) {
    UriComponentsBuilder b =
        UriComponentsBuilder.fromUriString("https://api.github.com")
            .path("/repos/")
            .pathSegment(owner, repo, "contents");
    for (String seg : filePath.split("/")) {
      if (!seg.isEmpty()) {
        b.pathSegment(seg);
      }
    }
    b.queryParam("ref", ref);
    return b.encode(StandardCharsets.UTF_8).build().toUri();
  }

  private static URI contentsPutUri(String owner, String repo, String filePath) {
    UriComponentsBuilder b =
        UriComponentsBuilder.fromUriString("https://api.github.com")
            .path("/repos/")
            .pathSegment(owner, repo, "contents");
    for (String seg : filePath.split("/")) {
      if (!seg.isEmpty()) {
        b.pathSegment(seg);
      }
    }
    return b.encode(StandardCharsets.UTF_8).build().toUri();
  }

  private JsonNode fetchContents(
      String owner, String repo, String path, String ref, AdminGitOpsPrProperties config) {
    try {
      return restClient
          .get()
          .uri(contentsUri(owner, repo, path, ref))
          .header("Authorization", bearer(config))
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientResponseException e) {
      if (e.getStatusCode().value() == 404) {
        return null;
      }
      log.warn("github_fetch_contents_failed status={}", e.getStatusCode().value());
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PROVIDER_FAILED",
          HttpStatus.BAD_GATEWAY,
          "GitHub API error while reading file");
    }
  }

  private String decodeContent(JsonNode contentNode) {
    if (!contentNode.isTextual()) {
      return "";
    }
    String b64 = contentNode.asText().replace("\n", "");
    byte[] raw = Base64.getDecoder().decode(b64.getBytes(StandardCharsets.UTF_8));
    return new String(raw, StandardCharsets.UTF_8);
  }

  private String resolveBranchSha(
      String owner, String repo, String branch, AdminGitOpsPrProperties config) {
    try {
      JsonNode ref =
          restClient
              .get()
              .uri("/repos/{owner}/{repo}/git/ref/heads/{branch}", owner, repo, branch)
              .header("Authorization", bearer(config))
              .header("Accept", "application/vnd.github+json")
              .header("X-GitHub-Api-Version", "2022-11-28")
              .retrieve()
              .body(JsonNode.class);
      if (ref != null && ref.path("object").path("sha").isTextual()) {
        return ref.path("object").path("sha").asText();
      }
    } catch (RestClientResponseException e) {
      log.warn("github_resolve_branch_failed status={}", e.getStatusCode().value());
    }
    throw new AdminGitOpsException(
        "ADMIN_GITOPS_PROVIDER_FAILED",
        HttpStatus.BAD_GATEWAY,
        "Could not resolve base branch on GitHub");
  }

  private String createBranchWithRetry(
      String owner, String repo, String head, String baseSha, AdminGitOpsPrProperties config) {
    String candidate = head;
    for (int i = 0; i < 8; i++) {
      try {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("ref", "refs/heads/" + candidate);
        body.put("sha", baseSha);
        restClient
            .post()
            .uri("/repos/{owner}/{repo}/git/refs", owner, repo)
            .header("Authorization", bearer(config))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toBodilessEntity();
        return candidate;
      } catch (RestClientResponseException e) {
        int code = e.getStatusCode().value();
        if (code == 422 && i < 7) {
          candidate = head + "-" + (i + 1);
          continue;
        }
        log.warn("github_create_branch_failed status={}", code);
        throw new AdminGitOpsException(
            "ADMIN_GITOPS_PROVIDER_FAILED", HttpStatus.BAD_GATEWAY, "Could not create Git branch");
      }
    }
    throw new AdminGitOpsException(
        "ADMIN_GITOPS_PROVIDER_FAILED",
        HttpStatus.BAD_GATEWAY,
        "Could not create Git branch after retries");
  }

  private static String resolveTargetEnvironment(GitOpsPrProviderRequest request) {
    String env = request.targetEnvironment() == null ? "" : request.targetEnvironment().trim();
    if (!env.isBlank()) {
      return env.toLowerCase(Locale.ROOT);
    }
    String path = request.valuesFilePath();
    int idx = path.indexOf("/environments/");
    if (idx < 0) {
      return "dev";
    }
    String rest = path.substring(idx + "/environments/".length());
    int slash = rest.indexOf('/');
    return (slash > 0 ? rest.substring(0, slash) : rest).toLowerCase(Locale.ROOT);
  }

  private void commitFileUpdate(
      String owner,
      String repo,
      String path,
      String branch,
      String fileShaNullable,
      String newContent,
      String message,
      AdminGitOpsPrProperties config) {
    try {
      ObjectNode body = objectMapper.createObjectNode();
      body.put("message", message);
      body.put(
          "content",
          Base64.getEncoder().encodeToString(newContent.getBytes(StandardCharsets.UTF_8)));
      if (fileShaNullable != null && !fileShaNullable.isBlank()) {
        body.put("sha", fileShaNullable);
      }
      body.put("branch", branch);
      restClient
          .put()
          .uri(contentsPutUri(owner, repo, path))
          .header("Authorization", bearer(config))
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      log.warn("github_commit_failed status={}", e.getStatusCode().value());
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PROVIDER_FAILED",
          HttpStatus.BAD_GATEWAY,
          "Could not commit patched file to GitHub");
    }
  }

  private JsonNode openPullRequest(
      String owner,
      String repo,
      String head,
      String base,
      String title,
      String prBody,
      AdminGitOpsPrProperties config) {
    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("title", title);
      root.put("body", prBody);
      root.put("head", head);
      root.put("base", base);
      return restClient
          .post()
          .uri("/repos/{owner}/{repo}/pulls", owner, repo)
          .header("Authorization", bearer(config))
          .header("Accept", "application/vnd.github+json")
          .header("X-GitHub-Api-Version", "2022-11-28")
          .contentType(MediaType.APPLICATION_JSON)
          .body(root)
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientResponseException e) {
      log.warn("github_open_pr_failed status={}", e.getStatusCode().value());
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PROVIDER_FAILED",
          HttpStatus.BAD_GATEWAY,
          "Could not open pull request on GitHub");
    }
  }
}
