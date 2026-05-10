package com.notebook.lumen.identity.admin.gitops;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminGitOpsPrProposalRepository extends JpaRepository<AdminGitOpsPrProposal, UUID> {

  Optional<AdminGitOpsPrProposal> findByIdempotencyKey(String idempotencyKey);

  Optional<AdminGitOpsPrProposal> findFirstByChangeRequestIdAndTargetEnvironmentAndProviderAndStatus(
      UUID changeRequestId, String targetEnvironment, String provider, GitOpsPrProposalStatus status);
}
