# GitHub GitOps provider (foundation)

When `ADMIN_GITOPS_PROVIDER=github` and `ADMIN_GITOPS_PR_ENABLED=true`, identity-service uses the **GitHub REST API** to:

1. Read `deploy/gitops/environments/{env}/values.yaml` from the **base branch**
2. Apply the **same allow-listed patch** as dry-run (`GitOpsYamlPatchService`)
3. Create a **head branch** (with collision suffix on `422`)
4. Commit the updated file on that branch
5. Open a **pull request** (no auto-merge)

## Configuration

Set:

- `ADMIN_GITOPS_REPOSITORY_OWNER`
- `ADMIN_GITOPS_REPOSITORY_NAME`
- `ADMIN_GITOPS_BASE_BRANCH` (e.g. `main`)
- `ADMIN_GITOPS_GITHUB_TOKEN` — **server-side only**; inject via Kubernetes Secret / External Secrets (Helm: optional key `admin-gitops-github-token` on the platform secret)

Never commit the token to GitOps repos or Helm values in clear text.

## Validation

`validateRepositoryAccess` performs a lightweight `GET /repos/{owner}/{repo}` before create. Failures surface as `ADMIN_GITOPS_PROVIDER_VALIDATION_FAILED` with audit; logs do **not** include the token.

## Limitations (Faz 80)

- Single file per PR; no multi-file or multi-env batches
- No merge policy or required-review integration
- If the repository file layout diverges from the allow-listed path, operations fail with `ADMIN_GITOPS_MAPPING_NOT_FOUND` or `ADMIN_GITOPS_PATCH_FAILED`

For local or CI without GitHub, use `ADMIN_GITOPS_PROVIDER=mock`.
