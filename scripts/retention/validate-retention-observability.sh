#!/usr/bin/env bash
#
# Validate platform retention Grafana dashboard JSON and Prometheus alert YAML (Faz 108).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
DASHBOARD="observability/grafana/dashboards/platform-retention-readiness.json"
ALERTS="observability/prometheus/alerts/platform-retention-readiness.yml"

python3 -m json.tool "$DASHBOARD" >/dev/null
echo "Grafana dashboard JSON OK: $DASHBOARD"

python3 - <<'PY'
import yaml
from pathlib import Path

alerts_path = Path("observability/prometheus/alerts/platform-retention-readiness.yml")
doc = yaml.safe_load(alerts_path.read_text(encoding="utf-8"))
groups = doc.get("groups") or []
rules = sum(len(g.get("rules") or []) for g in groups)
print(f"Prometheus alerts YAML OK: {alerts_path} (groups={len(groups)} rules={rules})")
PY

# High-cardinality / PII label guardrail (Faz 105 pattern).
for pattern in userId email workspaceId noteId requestId targetKey 'legal-hold-key'; do
  if grep -qE "$pattern" "$DASHBOARD" "$ALERTS" 2>/dev/null; then
    echo "Forbidden label/token '$pattern' found in retention observability assets" >&2
    exit 1
  fi
done

echo "Retention observability label guardrail OK."
