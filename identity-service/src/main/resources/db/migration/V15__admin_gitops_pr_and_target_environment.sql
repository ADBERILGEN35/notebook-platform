ALTER TABLE platform_admin_change_requests
  ADD COLUMN IF NOT EXISTS target_environment VARCHAR(32) NOT NULL DEFAULT 'staging';

CREATE TABLE IF NOT EXISTS admin_gitops_pr_proposals (
  id UUID PRIMARY KEY,
  change_request_id UUID NOT NULL REFERENCES platform_admin_change_requests(id),
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  target_environment VARCHAR(32) NOT NULL,
  base_branch VARCHAR(256) NOT NULL,
  branch_name VARCHAR(512),
  title VARCHAR(512) NOT NULL,
  body TEXT NOT NULL,
  changed_files JSONB NOT NULL,
  diff_summary JSONB,
  provider_pr_url VARCHAR(1024),
  provider_pr_number VARCHAR(64),
  last_error TEXT,
  created_by_user_id UUID NOT NULL REFERENCES users(id),
  idempotency_key VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL,
  pr_created_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS ix_admin_gitops_pr_proposals_change_request
  ON admin_gitops_pr_proposals(change_request_id);
CREATE INDEX IF NOT EXISTS ix_admin_gitops_pr_proposals_status
  ON admin_gitops_pr_proposals(status);

CREATE UNIQUE INDEX IF NOT EXISTS ux_admin_gitops_pr_idempotency
  ON admin_gitops_pr_proposals(idempotency_key)
  WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_admin_gitops_pr_success_per_cr_env_provider
  ON admin_gitops_pr_proposals(change_request_id, target_environment, provider)
  WHERE status = 'PR_CREATED';
