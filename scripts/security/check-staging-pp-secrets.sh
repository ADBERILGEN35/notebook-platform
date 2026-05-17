#!/usr/bin/env bash
#
# Report staging PP evidence secret readiness (names only — never values). Faz 133–134.
#
# Usage:
#   bash scripts/security/check-staging-pp-secrets.sh
#   bash scripts/security/check-staging-pp-secrets.sh --pp3-required true
#   bash scripts/security/check-staging-pp-secrets.sh --json
#   bash scripts/security/check-staging-pp-secrets.sh --check-github
#   bash scripts/security/check-staging-pp-secrets.sh --print-required
#
# Exit codes:
#   0 — PP-1 and PP-2 required secrets present (local env and/or GitHub names)
#   1 — PP-1 or PP-2 missing
#   2 — PP-3 required but retention secrets missing

set -euo pipefail

PP3_REQUIRED=false
JSON=false
CHECK_GITHUB=false
PRINT_REQUIRED=false

print_required_catalog() {
  cat <<'EOF'
==> Required staging PP secrets catalog (names only — no values)
Governance: docs/backend-staging-pp-secrets-governance.md

PP-1 SCIM delta sandbox (GitHub Actions)
  SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL
    Staging gateway base URL for diagnostics API
  SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN
    Short-lived platform admin JWT; rotate after evidence run
  SCIM_DELTA_SANDBOX_PROVIDER (optional variable)
    Default IdP: okta | entra | generic
  SCIM_DELTA_SANDBOX_EXPECT_READY (optional variable)
    Fail workflow when certificationResult is not certified

PP-1 cluster / IdP (not GitHub — ExternalSecret / Vault)
  scimDeltaRemoteFetch.bearerTokenFromSecret
    Sandbox IdP SCIM bearer; rotate with IdP sandbox policy

PP-2 Break-glass revocation drill (GitHub Actions)
  BREAK_GLASS_STAGING_API_BASE_URL
    Staging gateway base URL
  BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN
    Admin JWT for revoke API; revoke or expire after drill
  BREAK_GLASS_STAGING_EMERGENCY_TOKEN (one of)
    Shortest-TTL staging emergency credential; rotate immediately after drill
  BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN (one of)
    Pre-issued break-glass JWT for drill; rotate after drill
  BREAK_GLASS_STAGING_TEST_ENDPOINT (optional variable)
    Admin route for post-revoke deny test
  BREAK_GLASS_STAGING_IMAGE_TAG (optional variable)
    Image tag recorded in evidence metadata

PP-3 Retention staging smoke (only if pp3_required=true)
  RETENTION_STAGING_API_BASE_URL
  RETENTION_STAGING_ADMIN_ACCESS_TOKEN
  RETENTION_EXPECT_*_READY (optional variables)

Allowed workflows: workflow_dispatch only (scim-delta-readiness, break-glass-revocation-readiness)
Forbidden: storing values in repo, logs, CR text, or evidence JSON
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --pp3-required) PP3_REQUIRED="$2"; shift 2 ;;
    --json) JSON=true; shift ;;
    --check-github) CHECK_GITHUB=true; shift ;;
    --print-required) PRINT_REQUIRED=true; shift ;;
    -h|--help)
      echo "Usage: $0 [--pp3-required true|false] [--json] [--check-github] [--print-required]"
      exit 0
      ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

if [[ "$PRINT_REQUIRED" == true ]]; then
  print_required_catalog
  exit 0
fi

pp1_url=missing
pp1_admin=missing
pp1_provider=missing
pp2_url=missing
pp2_admin=missing
pp2_bg_cred=missing
pp2_endpoint=missing
pp3_url=missing
pp3_admin=missing

[[ -n "${SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL:-}" ]] && pp1_url=present
[[ -n "${SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN:-}" ]] && pp1_admin=present
[[ -n "${SCIM_DELTA_SANDBOX_PROVIDER:-}" ]] && pp1_provider=present

[[ -n "${BREAK_GLASS_STAGING_API_BASE_URL:-}" ]] && pp2_url=present
[[ -n "${BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN:-}" ]] && pp2_admin=present
if [[ -n "${BREAK_GLASS_STAGING_EMERGENCY_TOKEN:-}" || -n "${BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN:-}" ]]; then
  pp2_bg_cred=present
fi
[[ -n "${BREAK_GLASS_STAGING_TEST_ENDPOINT:-}" ]] && pp2_endpoint=present

[[ -n "${RETENTION_STAGING_API_BASE_URL:-}" ]] && pp3_url=present
[[ -n "${RETENTION_STAGING_ADMIN_ACCESS_TOKEN:-}" ]] && pp3_admin=present

pp1_local_ready=false
[[ "$pp1_url" == present && "$pp1_admin" == present ]] && pp1_local_ready=true

pp2_local_ready=false
[[ "$pp2_url" == present && "$pp2_admin" == present && "$pp2_bg_cred" == present ]] && pp2_local_ready=true

pp3_local_ready=false
[[ "$pp3_url" == present && "$pp3_admin" == present ]] && pp3_local_ready=true

gh_pp1=false
gh_pp2=false
gh_pp3=false
gh_checked=false
gh_staging_env=absent

if [[ "$CHECK_GITHUB" == true ]]; then
  if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    gh_checked=true
    names="$(gh secret list 2>/dev/null | awk '{print $1}' || true)"
    env_names=""
    if gh secret list --env staging >/dev/null 2>&1; then
      gh_staging_env=present
      env_names="$(gh secret list --env staging 2>/dev/null | awk '{print $1}' || true)"
    fi
    has_name() {
      echo "$names" | grep -qx "$1" && return 0
      [[ -n "$env_names" ]] && echo "$env_names" | grep -qx "$1" && return 0
      return 1
    }
    if has_name SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL && has_name SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN; then
      gh_pp1=true
    fi
    if has_name BREAK_GLASS_STAGING_API_BASE_URL && has_name BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN; then
      if has_name BREAK_GLASS_STAGING_EMERGENCY_TOKEN || has_name BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN; then
        gh_pp2=true
      fi
    fi
    if has_name RETENTION_STAGING_API_BASE_URL && has_name RETENTION_STAGING_ADMIN_ACCESS_TOKEN; then
      gh_pp3=true
    fi
  else
    echo "warning: --check-github skipped (gh not installed or not authenticated)" >&2
  fi
fi

pp1_readiness=MISSING_SECRET
if [[ "$pp1_local_ready" == true ]]; then
  pp1_readiness=READY_LOCAL
elif [[ "$gh_pp1" == true ]]; then
  pp1_readiness=READY_GITHUB
fi

pp2_readiness=MISSING_SECRET
if [[ "$pp2_local_ready" == true ]]; then
  pp2_readiness=READY_LOCAL
elif [[ "$gh_pp2" == true ]]; then
  pp2_readiness=READY_GITHUB
fi

pp3_readiness=NOT_REQUIRED
if [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]]; then
  if [[ "$pp3_local_ready" == true ]]; then
    pp3_readiness=READY_LOCAL
  elif [[ "$gh_pp3" == true ]]; then
    pp3_readiness=READY_GITHUB
  else
    pp3_readiness=MISSING_SECRET
  fi
fi

if [[ "$JSON" == true ]]; then
  pp3_req_json=false
  [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]] && pp3_req_json=true
  gh_chk_json=false
  [[ "$gh_checked" == true ]] && gh_chk_json=true
  export PP1_READINESS="$pp1_readiness" PP1_URL="$pp1_url" PP1_ADMIN="$pp1_admin" PP1_PROVIDER="$pp1_provider"
  export PP2_READINESS="$pp2_readiness" PP2_URL="$pp2_url" PP2_ADMIN="$pp2_admin" PP2_BG="$pp2_bg_cred" PP2_EP="$pp2_endpoint"
  export PP3_READINESS="$pp3_readiness" PP3_URL="$pp3_url" PP3_ADMIN="$pp3_admin"
  export PP3_REQ_JSON="$pp3_req_json" GH_CHK_JSON="$gh_chk_json" GH_STAGING_ENV="$gh_staging_env"
  python3 - <<'PY'
import json, os
print(json.dumps({
  "pp1": {
    "readiness": os.environ["PP1_READINESS"],
    "SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL": os.environ["PP1_URL"],
    "SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN": os.environ["PP1_ADMIN"],
    "SCIM_DELTA_SANDBOX_PROVIDER": os.environ["PP1_PROVIDER"],
  },
  "pp2": {
    "readiness": os.environ["PP2_READINESS"],
    "BREAK_GLASS_STAGING_API_BASE_URL": os.environ["PP2_URL"],
    "BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN": os.environ["PP2_ADMIN"],
    "break_glass_credential": os.environ["PP2_BG"],
    "BREAK_GLASS_STAGING_TEST_ENDPOINT": os.environ["PP2_EP"],
  },
  "pp3": {
    "readiness": os.environ["PP3_READINESS"],
    "pp3_required": os.environ["PP3_REQ_JSON"] == "true",
    "RETENTION_STAGING_API_BASE_URL": os.environ["PP3_URL"],
    "RETENTION_STAGING_ADMIN_ACCESS_TOKEN": os.environ["PP3_ADMIN"],
  },
  "github_secret_names_checked": os.environ["GH_CHK_JSON"] == "true",
  "github_environment_staging": os.environ["GH_STAGING_ENV"],
  "note": "Workflows use repository secrets; environment-only secrets require job environment: staging",
}, indent=2))
PY
else
  echo "==> Staging PP secrets readiness (present/missing only)"
  echo ""
  echo "PP-1 SCIM delta sandbox: $pp1_readiness"
  echo "  SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL: $pp1_url"
  echo "  SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN: $pp1_admin"
  echo "  SCIM_DELTA_SANDBOX_PROVIDER: $pp1_provider (optional; workflow default okta)"
  echo ""
  echo "PP-2 Break-glass revocation: $pp2_readiness"
  echo "  BREAK_GLASS_STAGING_API_BASE_URL: $pp2_url"
  echo "  BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN: $pp2_admin"
  echo "  break_glass_credential (EMERGENCY or BREAK_GLASS token): $pp2_bg_cred"
  echo "  BREAK_GLASS_STAGING_TEST_ENDPOINT: $pp2_endpoint (optional)"
  echo ""
  echo "PP-3 Retention smoke: $pp3_readiness"
  if [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]]; then
    echo "  RETENTION_STAGING_API_BASE_URL: $pp3_url"
    echo "  RETENTION_STAGING_ADMIN_ACCESS_TOKEN: $pp3_admin"
  else
    echo "  (pp3_required=false — retention secrets not required for bundle)"
  fi
  if [[ "$CHECK_GITHUB" == true ]]; then
    echo ""
    echo "GitHub secret names (repository + environment staging if present):"
    echo "  environment staging: $gh_staging_env"
    echo "  PP-1 names configured: $([[ "$gh_pp1" == true ]] && echo present || echo missing)"
    echo "  PP-2 names configured: $([[ "$gh_pp2" == true ]] && echo present || echo missing)"
    echo "  PP-3 names configured: $([[ "$gh_pp3" == true ]] && echo present || echo missing)"
  fi
  echo ""
  echo "Summary:"
  echo "  PP-1: $pp1_readiness"
  echo "  PP-2: $pp2_readiness"
  echo "  PP-3: $pp3_readiness"
fi

exit_code=0
if [[ "$pp1_readiness" == "MISSING_SECRET" || "$pp2_readiness" == "MISSING_SECRET" ]]; then
  exit_code=1
fi
if [[ "${PP3_REQUIRED,,}" == "true" || "${PP3_REQUIRED}" == "1" ]]; then
  if [[ "$pp3_readiness" == "MISSING_SECRET" ]]; then
    exit_code=2
  fi
fi

exit "$exit_code"
