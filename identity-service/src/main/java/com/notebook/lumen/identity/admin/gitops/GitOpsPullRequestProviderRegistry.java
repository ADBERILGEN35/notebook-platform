package com.notebook.lumen.identity.admin.gitops;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GitOpsPullRequestProviderRegistry {

  private final AdminGitOpsPrProperties properties;
  private final MockGitOpsPullRequestProvider mockGitOpsPullRequestProvider;
  private final GithubGitOpsPullRequestProvider githubGitOpsPullRequestProvider;

  public GitOpsPullRequestProviderRegistry(
      AdminGitOpsPrProperties properties,
      MockGitOpsPullRequestProvider mockGitOpsPullRequestProvider,
      GithubGitOpsPullRequestProvider githubGitOpsPullRequestProvider) {
    this.properties = properties;
    this.mockGitOpsPullRequestProvider = mockGitOpsPullRequestProvider;
    this.githubGitOpsPullRequestProvider = githubGitOpsPullRequestProvider;
  }

  public GitOpsPullRequestProvider requireProvider() {
    String p = properties.provider();
    if ("github".equals(p)) {
      return githubGitOpsPullRequestProvider;
    }
    if ("mock".equals(p)) {
      return mockGitOpsPullRequestProvider;
    }
    throw new AdminGitOpsException(
        "ADMIN_GITOPS_PROVIDER_FAILED",
        HttpStatus.BAD_REQUEST,
        "Unsupported GitOps provider: " + p);
  }
}
