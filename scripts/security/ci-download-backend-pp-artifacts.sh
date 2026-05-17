#!/usr/bin/env bash
#
# CI: download-backend-pp-artifacts.sh (bash -n only; no live gh download)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

echo "==> bash -n download-backend-pp-artifacts.sh"
bash -n scripts/security/download-backend-pp-artifacts.sh

echo "download-backend-pp-artifacts CI passed (syntax only; live gh download is operator-driven)."
