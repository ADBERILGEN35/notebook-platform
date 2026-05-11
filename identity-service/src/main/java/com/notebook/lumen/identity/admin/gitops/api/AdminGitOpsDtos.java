package com.notebook.lumen.identity.admin.gitops.api;

import java.util.List;
import java.util.UUID;

public final class AdminGitOpsDtos {
  private AdminGitOpsDtos() {}

  public record DryRunBody(String targetEnvironment) {}

  public record CreatePrBody(
      String targetEnvironment, String idempotencyKey, String confirmation) {}

  public record PathChange(String yamlPath, String oldValue, String newValue) {}

  public record FileChangePreview(String path, List<PathChange> changes) {}

  public record DryRunResponse(
      UUID changeRequestId,
      String targetEnvironment,
      String provider,
      List<FileChangePreview> changedFiles,
      String diffPreview,
      List<String> warnings) {}

  public record CreatePrResponse(
      UUID proposalId,
      String status,
      String provider,
      String providerPrUrl,
      String branchName,
      String nextStep) {}
}
