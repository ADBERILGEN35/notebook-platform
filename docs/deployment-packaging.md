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
- Provide JWT keys, DB credentials and internal tokens via a secret manager.
- Use `.env.production.example` only as a template; never commit populated `.env` files.
- Run `scripts/check-no-secrets.sh` before publishing deployment changes.
- Add image scanning, SBOM generation and signed immutable image tags before real production rollout.
