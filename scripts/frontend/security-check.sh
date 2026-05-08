#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"

echo "[security-check] scanning for dangerouslySetInnerHTML..."
if grep -RIn "dangerouslySetInnerHTML" "$FRONTEND_DIR/src"; then
  echo "[security-check] found dangerouslySetInnerHTML usage" >&2
  exit 1
fi

echo "[security-check] scanning for runtime auth token localStorage usage..."
if grep -RInE "localStorage.*(access|refresh).*token|ACCESS_TOKEN_KEY|REFRESH_TOKEN_KEY" "$FRONTEND_DIR/src"; then
  echo "[security-check] note: localStorage token paths exist (allowed only for bearer compatibility)." >&2
fi

echo "[security-check] checking index.html for inline script tags..."
if grep -nE "<script[^>]*>" "$FRONTEND_DIR/index.html" | grep -v "src="; then
  echo "[security-check] inline script detected in index.html" >&2
  exit 1
fi

echo "[security-check] checking runtime-config external script reference..."
if ! grep -n "<script src=\"/runtime-config.js\"></script>" "$FRONTEND_DIR/index.html" >/dev/null; then
  echo "[security-check] runtime-config external script include missing" >&2
  exit 1
fi

echo "[security-check] npm audit (high+) report (warn-only)..."
(
  cd "$FRONTEND_DIR"
  npm audit --audit-level=high || true
)

echo "[security-check] completed."
