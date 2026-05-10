package com.notebook.lumen.identity.admin.gitops;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.HttpStatus;

@ConfigurationProperties(prefix = "identity.admin.gitops")
public record AdminGitOpsPrProperties(
    @DefaultValue("false") boolean enabled,
    String provider,
    String allowedEnvironments,
    String defaultEnvironment,
    String baseBranch,
    String branchPrefix,
    String repositoryOwner,
    String repositoryName,
    String githubToken,
    @DefaultValue("true") boolean requireApprovedChange) {

  public AdminGitOpsPrProperties {
    provider = provider == null || provider.isBlank() ? "mock" : provider.trim().toLowerCase(Locale.ROOT);
    allowedEnvironments =
        allowedEnvironments == null || allowedEnvironments.isBlank()
            ? "dev,staging,prod"
            : allowedEnvironments;
    defaultEnvironment =
        defaultEnvironment == null || defaultEnvironment.isBlank() ? "staging" : defaultEnvironment.trim();
    baseBranch = baseBranch == null || baseBranch.isBlank() ? "main" : baseBranch.trim();
    branchPrefix =
        branchPrefix == null || branchPrefix.isBlank() ? "admin-change" : branchPrefix.trim().toLowerCase(Locale.ROOT);
    repositoryOwner = repositoryOwner == null ? "" : repositoryOwner.trim();
    repositoryName = repositoryName == null ? "" : repositoryName.trim();
    githubToken = githubToken == null ? "" : githubToken.trim();
  }

  public Set<String> allowedEnvironmentSet() {
    return Arrays.stream(allowedEnvironments.split(","))
        .map(s -> s.trim().toLowerCase(Locale.ROOT))
        .filter(s -> !s.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }

  public void validateEnvironment(String env) {
    if (env == null || env.isBlank()) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_ENVIRONMENT_NOT_ALLOWED", HttpStatus.BAD_REQUEST, "targetEnvironment is required");
    }
    String n = env.trim().toLowerCase(Locale.ROOT);
    if (!allowedEnvironmentSet().contains(n)) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_ENVIRONMENT_NOT_ALLOWED",
          HttpStatus.BAD_REQUEST,
          "Environment not allowed: " + env);
    }
  }
}
