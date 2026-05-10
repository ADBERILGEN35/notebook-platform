package com.notebook.lumen.identity.admin.gitops;

public record GitOpsPrProviderRequest(
    String repositoryOwner,
    String repositoryName,
    String baseBranch,
    String headBranch,
    String valuesFilePath,
    String commitMessage,
    String prTitle,
    String prBody,
    String operationType,
    String normalizedRequestedValue) {}
