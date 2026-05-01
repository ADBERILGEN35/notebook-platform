#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if git -C "$ROOT_DIR" ls-files --error-unmatch .env >/dev/null 2>&1; then
  echo "Refusing committed .env file. Commit .env.example templates only." >&2
  exit 1
fi

if grep -RIn --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=build \
  --exclude='*.md' --exclude='.env.example' --exclude='.env.production.example' \
  --exclude='check-no-secrets.sh' \
  -- '-----BEGIN \(RSA \)\?PRIVATE KEY-----' "$ROOT_DIR"; then
  echo "Private key material must not be committed." >&2
  exit 1
fi

if grep -RIn --exclude-dir=.git --exclude-dir=.gradle --exclude-dir=build \
  --exclude='*.md' --exclude='.env.example' --exclude='.env.production.example' \
  --exclude='check-no-secrets.sh' \
  -E -- '(PASSWORD|TOKEN|SECRET|PRIVATE_KEY)[A-Za-z0-9_ -]*[:=][[:space:]]*[A-Za-z0-9+/=._-]{24,}' \
  "$ROOT_DIR"; then
  echo "Possible hard-coded secret detected." >&2
  exit 1
fi

if grep -RIn \
  -E -- '(^|[[:space:]])(password|token|secret|privateKey|private-key|clientSecret):[[:space:]]*["'\'']?[A-Za-z0-9+/=._-]{16,}' \
  "$ROOT_DIR/deploy/helm/notebook-platform/values-prod.example.yaml"; then
  echo "values-prod.example.yaml must not contain real-looking secret values." >&2
  exit 1
fi

if grep -RIn --include='*.yaml' --include='*.tpl' \
  -E -- '(stringData|data):[[:space:]]*[A-Za-z0-9+/=._-]{24,}' \
  "$ROOT_DIR/deploy/helm/notebook-platform/templates"; then
  echo "Helm templates must not hardcode secret payloads." >&2
  exit 1
fi

echo "No obvious committed secrets detected."
