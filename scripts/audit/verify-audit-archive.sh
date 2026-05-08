#!/usr/bin/env bash
set -euo pipefail

FILE_PATH="${FILE:-}"
MANIFEST_PATH="${MANIFEST:-}"

if [[ -z "$FILE_PATH" || -z "$MANIFEST_PATH" ]]; then
  echo "Usage: FILE=<archive> MANIFEST=<manifest.json> bash scripts/audit/verify-audit-archive.sh" >&2
  exit 1
fi
[[ -f "$FILE_PATH" ]] || { echo "Archive file not found: $FILE_PATH" >&2; exit 1; }
[[ -f "$MANIFEST_PATH" ]] || { echo "Manifest file not found: $MANIFEST_PATH" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required." >&2; exit 1; }

calc_sha() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

line_count() {
  if [[ "$1" == *.gz ]]; then
    gzip -cd "$1" | wc -l | tr -d ' '
  else
    wc -l < "$1" | tr -d ' '
  fi
}

python3 - "$MANIFEST_PATH" "$(calc_sha "$FILE_PATH")" "$(line_count "$FILE_PATH")" "$(basename "$FILE_PATH")" <<'PY'
import json
import sys

manifest_path, actual_sha, actual_count, file_name = sys.argv[1:5]
with open(manifest_path, "r", encoding="utf-8") as f:
    m = json.load(f)

required = ["exportId", "source", "format", "recordCount", "checksumSha256", "fileName", "schemaVersion"]
missing = [k for k in required if k not in m]
if missing:
    raise SystemExit(f"Manifest missing required keys: {missing}")

if str(m["checksumSha256"]).lower() != actual_sha.lower():
    raise SystemExit("Checksum mismatch.")
if int(m["recordCount"]) != int(actual_count):
    raise SystemExit("Record count mismatch.")
if m["fileName"] != file_name:
    raise SystemExit("Manifest fileName mismatch.")

print("Archive verification successful.")
PY
