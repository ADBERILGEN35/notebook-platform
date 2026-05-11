package com.notebook.lumen.identity.admin.gitops;

public record GitOpsPrProviderResult(
    String providerPrUrl, String providerPrNumber, String headBranch) {}
