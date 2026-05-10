package com.notebook.lumen.identity.admin.gitops;

/** Server-side Git provider for opening PRs from approved admin change requests. */
public interface GitOpsPullRequestProvider {

  String getProviderName();

  void validateRepositoryAccess(AdminGitOpsPrProperties config);

  GitOpsPrProviderResult createPullRequest(GitOpsPrProviderRequest request, AdminGitOpsPrProperties config);
}
