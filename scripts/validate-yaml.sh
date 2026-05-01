#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is not installed; skipping YAML syntax validation." >&2
  exit 0
fi

if ! python3 -c "import yaml" >/dev/null 2>&1; then
  echo "PyYAML is not installed; skipping YAML syntax validation." >&2
  exit 0
fi

mapfile -t yaml_files < <(
  {
    find "$ROOT_DIR/deploy/gitops" "$ROOT_DIR/deploy/policies" "$ROOT_DIR/.github/workflows" \
      -type f \( -name "*.yaml" -o -name "*.yml" \)
    find "$ROOT_DIR/deploy/helm" \
      -type f \( -name "Chart.yaml" -o -name "values*.yaml" -o -path "*/examples/*.yaml" \)
  } | sort
)

if [[ "${#yaml_files[@]}" -eq 0 ]]; then
  echo "No YAML files found."
  exit 0
fi

python3 - "${yaml_files[@]}" <<'PY'
import pathlib
import sys

import yaml

failed = False
for raw_path in sys.argv[1:]:
    path = pathlib.Path(raw_path)
    try:
        with path.open("r", encoding="utf-8") as handle:
            list(yaml.safe_load_all(handle))
    except Exception as exc:
        failed = True
        print(f"{path}: {exc}", file=sys.stderr)

if failed:
    sys.exit(1)
PY

echo "Validated ${#yaml_files[@]} YAML files."
