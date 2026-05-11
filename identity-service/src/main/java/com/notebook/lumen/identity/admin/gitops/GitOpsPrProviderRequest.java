package com.notebook.lumen.identity.admin.gitops;

import java.util.UUID;

public record GitOpsPrProviderRequest(
    String repositoryOwner,
    String repositoryName,
    String baseBranch,
    String headBranch,
    String valuesFilePath,
    String targetEnvironment,
    String commitMessage,
    String prTitle,
    String prBody,
    String operationType,
    String normalizedRequestedValue,
    UUID rbacChangeRequestId,
    UUID rbacRequestedByUserId,
    UUID rbacApprovedByUserId) {

  public GitOpsPrProviderRequest(
      String repositoryOwner,
      String repositoryName,
      String baseBranch,
      String headBranch,
      String valuesFilePath,
      String commitMessage,
      String prTitle,
      String prBody,
      String operationType,
      String normalizedRequestedValue) {
    this(
        repositoryOwner,
        repositoryName,
        baseBranch,
        headBranch,
        valuesFilePath,
        "",
        commitMessage,
        prTitle,
        prBody,
        operationType,
        normalizedRequestedValue,
        null,
        null,
        null);
  }
}
