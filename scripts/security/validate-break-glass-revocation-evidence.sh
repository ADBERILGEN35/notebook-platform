#!/usr/bin/env bash
#
# Validate break-glass revocation evidence JSON (Faz 123).
# Exit 0 ok, 2 privacy violation, 1 schema error

set -euo pipefail

FILE="${1:-}"
if [[ -z "$FILE" || ! -f "$FILE" ]]; then
  echo "usage: $0 <evidence.json>" >&2
  exit 1
fi

python3 -m json.tool "$FILE" >/dev/null

REQUIRED_KEYS=(
  evidenceSchemaVersion
  generatedAt
  environment
  result
  gatewayRejectErrorCode
  jtiMasked
  sessionIdShort
)

for key in "${REQUIRED_KEYS[@]}"; do
  if ! python3 -c "import json,sys; d=json.load(open(sys.argv[1])); sys.exit(0 if '$key' in d else 1)" "$FILE"; then
    echo "schema_error: missing key $key" >&2
    exit 1
  fi
done

SCHEMA="$(python3 -c "import json; print(json.load(open('$FILE'))['evidenceSchemaVersion'])")"
if [[ "$SCHEMA" != "break-glass-revocation-evidence-v1" ]]; then
  echo "schema_error: unexpected schema $SCHEMA" >&2
  exit 1
fi

FORBIDDEN='Bearer [A-Za-z0-9._-]{8,}|eyJ[A-Za-z0-9_-]{10,}|"access_token"|Authorization:|password|emergency.token'
if grep -qE "$FORBIDDEN" "$FILE"; then
  echo "privacy_violation: forbidden pattern in evidence file" >&2
  exit 2
fi

RESULT="$(python3 -c "import json; print(json.load(open('$FILE'))['result'])")"
case "$RESULT" in
  passed|skipped|failed|privacy-failure|readiness-gap|shape-mismatch) ;;
  *)
    echo "schema_error: invalid result $RESULT" >&2
    exit 1
    ;;
esac

echo "evidence validation passed"
