package com.notebook.lumen.identity.admin.gitops;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MockGitOpsPullRequestProvider implements GitOpsPullRequestProvider {

  @Override
  public String getProviderName() {
    return "mock";
  }

  @Override
  public void validateRepositoryAccess(AdminGitOpsPrProperties config) {
    // No external calls.
  }

  @Override
  public GitOpsPrProviderResult createPullRequest(
      GitOpsPrProviderRequest request, AdminGitOpsPrProperties config) {
    if (request.headBranch() == null || request.headBranch().isBlank()) {
      throw new AdminGitOpsException(
          "ADMIN_GITOPS_PROVIDER_FAILED",
          HttpStatus.BAD_REQUEST,
          "headBranch is required for mock provider");
    }
    String num =
        String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits()) % 90000 + 10000);
    String url = "https://mock.gitops.invalid/pull/" + num;
    return new GitOpsPrProviderResult(url, num, request.headBranch());
  }
}
