package com.notebook.lumen.identity.admin.gitops;

import java.util.UUID;

/** Context required to append a row to {@code admin-rbac-overrides.yaml} (governance manifest only). */
public record GitOpsRbacPatchContext(UUID changeRequestId, UUID requestedByUserId, UUID approvedByUserId) {}
