# GitOps Layout

This directory contains provider-agnostic GitOps examples for the existing Helm chart. It does not
install Argo CD, deploy to a real cluster or contain real secret values.

## Structure

- `argocd/app-project.yaml`: namespace and repository boundary example.
- `argocd/app-dev.yaml`: dev Application with automated sync and pruning.
- `argocd/app-staging.yaml`: staging Application with self-heal enabled and pruning disabled.
- `argocd/app-prod.yaml`: prod Application with manual sync.
- `environments/dev/values.yaml`: low-cost dev overrides.
- `environments/staging/values.yaml`: External Secrets, NetworkPolicy and observability enabled.
- `environments/prod/values.yaml`: production-oriented overrides with HPA enabled.

Replace `https://github.com/ORG/REPO.git`, registry placeholders, hosts and SecretStore names
before using these examples.

## Promotion

Promote immutable image tags by changing only the target environment values file. Dev can auto-sync,
staging should run smoke validation, and prod should be changed through a manually approved PR.

Production promotion should also require:

- per-service SBOM artifact
- Trivy CRITICAL-free scan result
- signed image status
- provenance attestation or documented placeholder state
- immutable tag, with digest pinning preferred when digests are available

## Security

- Never commit real secrets.
- Prefer External Secrets for staging and prod.
- Keep prod Argo CD sync manual or approval-gated.
- Use immutable image tags, not `latest`.
- Prefer `image.digest` for prod once registry digest capture is stable.
- Connect SBOM generation and image vulnerability scanning to release gates.
- Do not promote unsigned images to prod without a documented emergency exception.
- Do not run FORCE RLS SQL through GitOps auto-sync.
