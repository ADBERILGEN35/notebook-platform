# Deployment Packaging

## Docker Images

Each service Dockerfile uses:

- JDK build stage
- JRE runtime stage
- non-root `app` user
- runtime `JAVA_OPTS`
- no baked secrets

Secrets must be supplied at runtime through environment variables, Docker secrets or the target platform secret manager. File-based secret paths such as `/run/secrets/jwt_private_key`, `/run/secrets/jwt_public_key` and `/run/secrets/internal_api_token` are supported as the deployment foundation.

## Build

```bash
./gradlew clean check
./gradlew bootJar
docker compose build
```

## Runtime JVM Options

All service images support:

```bash
JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
```

Tune memory per container instead of baking JVM flags into images.

## Compose Modes

- `docker-compose.dev.yml`: local dependencies only, for IDE/Gradle service runs.
- `docker-compose.yml`: full application stack.
- `docker-compose.observability.yml`: optional Prometheus and OTel Collector profile.

## Production Notes

- Do not expose service ports directly to the public internet.
- Route public traffic through the gateway.
- Keep workspace internal endpoints off public routes.
- Set `SPRING_PROFILES_ACTIVE=prod` for production-like validation.
- Provide JWT keys, service JWT keys, DB credentials, Redis credentials and internal fallback tokens
  through External Secrets or another runtime secret manager path.
- Use `.env.production.example` only as a template; never commit populated `.env` files.
- Run `scripts/check-no-secrets.sh` before publishing deployment changes.
- Add image scanning, SBOM generation and signed immutable image tags before real production rollout.

## Kubernetes / Helm

Faz 14 adds a provider-agnostic Helm chart:

- `deploy/helm/notebook-platform`
- `deploy/helm/notebook-platform/values-dev.yaml`
- `deploy/helm/notebook-platform/values-prod.example.yaml`

The chart renders Deployments, ClusterIP Services, ConfigMap, native Secret/pre-created Secret
references, optional ExternalSecret, optional Ingress, optional NetworkPolicy and optional
ServiceMonitor. PostgreSQL, Redis, OTel Collector and the actual secret manager remain external
dependencies.

Validation:

```bash
bash scripts/helm-template-check.sh
```

When Helm is installed, the script runs `helm lint` and renders default, dev, prod example,
ExternalSecret and existingSecret modes. If Helm is not installed, it exits successfully with a
clear skip message.

Detailed deployment guidance: [`kubernetes-deployment.md`](kubernetes-deployment.md).
Secret delivery guidance: [`external-secrets.md`](external-secrets.md).
Runtime RLS staged rollout guidance: [`runtime-rls-rollout.md`](runtime-rls-rollout.md).
