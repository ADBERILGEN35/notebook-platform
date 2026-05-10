package com.notebook.lumen.identity.admin.gitops;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin_gitops_pr_proposals")
public class AdminGitOpsPrProposal {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "change_request_id", nullable = false)
  private UUID changeRequestId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 32)
  private GitOpsPrProposalStatus status;

  @Column(name = "provider", nullable = false, length = 32)
  private String provider;

  @Column(name = "target_environment", nullable = false, length = 32)
  private String targetEnvironment;

  @Column(name = "base_branch", nullable = false, length = 256)
  private String baseBranch;

  @Column(name = "branch_name", length = 512)
  private String branchName;

  @Column(name = "title", nullable = false, length = 512)
  private String title;

  @Column(name = "body", nullable = false, columnDefinition = "TEXT")
  private String body;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "changed_files", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> changedFiles;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "diff_summary", columnDefinition = "jsonb")
  private Map<String, Object> diffSummary;

  @Column(name = "provider_pr_url", length = 1024)
  private String providerPrUrl;

  @Column(name = "provider_pr_number", length = 64)
  private String providerPrNumber;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "idempotency_key", length = 128)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "pr_created_at")
  private Instant prCreatedAt;

  protected AdminGitOpsPrProposal() {}

  public AdminGitOpsPrProposal(
      UUID id,
      UUID changeRequestId,
      GitOpsPrProposalStatus status,
      String provider,
      String targetEnvironment,
      String baseBranch,
      String branchName,
      String title,
      String body,
      Map<String, Object> changedFiles,
      Map<String, Object> diffSummary,
      String providerPrUrl,
      String providerPrNumber,
      String lastError,
      UUID createdByUserId,
      String idempotencyKey,
      Instant createdAt,
      Instant prCreatedAt) {
    this.id = id;
    this.changeRequestId = changeRequestId;
    this.status = status;
    this.provider = provider;
    this.targetEnvironment = targetEnvironment;
    this.baseBranch = baseBranch;
    this.branchName = branchName;
    this.title = title;
    this.body = body;
    this.changedFiles = changedFiles;
    this.diffSummary = diffSummary;
    this.providerPrUrl = providerPrUrl;
    this.providerPrNumber = providerPrNumber;
    this.lastError = lastError;
    this.createdByUserId = createdByUserId;
    this.idempotencyKey = idempotencyKey;
    this.createdAt = createdAt;
    this.prCreatedAt = prCreatedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getChangeRequestId() {
    return changeRequestId;
  }

  public GitOpsPrProposalStatus getStatus() {
    return status;
  }

  public void setStatus(GitOpsPrProposalStatus status) {
    this.status = status;
  }

  public String getProvider() {
    return provider;
  }

  public String getTargetEnvironment() {
    return targetEnvironment;
  }

  public String getBaseBranch() {
    return baseBranch;
  }

  public String getBranchName() {
    return branchName;
  }

  public void setBranchName(String branchName) {
    this.branchName = branchName;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public Map<String, Object> getChangedFiles() {
    return changedFiles;
  }

  public Map<String, Object> getDiffSummary() {
    return diffSummary;
  }

  public void setDiffSummary(Map<String, Object> diffSummary) {
    this.diffSummary = diffSummary;
  }

  public String getProviderPrUrl() {
    return providerPrUrl;
  }

  public void setProviderPrUrl(String providerPrUrl) {
    this.providerPrUrl = providerPrUrl;
  }

  public String getProviderPrNumber() {
    return providerPrNumber;
  }

  public void setProviderPrNumber(String providerPrNumber) {
    this.providerPrNumber = providerPrNumber;
  }

  public String getLastError() {
    return lastError;
  }

  public void setLastError(String lastError) {
    this.lastError = lastError;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getPrCreatedAt() {
    return prCreatedAt;
  }

  public void setPrCreatedAt(Instant prCreatedAt) {
    this.prCreatedAt = prCreatedAt;
  }
}
