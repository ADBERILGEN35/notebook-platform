# Admission Policy Examples

These examples document supply-chain admission controls for a future cluster rollout. They are not
installed by Helm or GitOps in Faz 21.

## Options

- Sigstore policy-controller: verifies Cosign signatures and attestations with ClusterImagePolicy.
- Kyverno: validates image signatures, tags and Pod security properties with admission policies.
- Connaisseur: verifies image signatures at admission with pluggable trust roots.
- OPA Gatekeeper: enforces tag, digest and Pod security rules through constraint templates.

## Policy Intent

Production should eventually enforce:

- no `latest` image tags
- signed images from the approved workflow identity
- optional digest pinning for production workloads
- non-root containers with privilege escalation disabled
- SBOM and vulnerability gates before GitOps promotion

The example manifests contain placeholder repository, issuer and identity values. Replace them
before applying to a real cluster.
