# Faz 120: SCIM Delta Sandbox Evidence + Provider Certification Checklist

## Hedef

Okta, Microsoft Entra ID ve generic SCIM için canlı sandbox dry-run kanıtının nasıl toplanacağını, kabul kriterlerini, bloklayıcıları ve production scheduler öncesi certification adımlarını netleştirmek.

## Scope

| Deliverable | Path |
|-------------|------|
| Sandbox evidence spec | `docs/scim-delta-sandbox-evidence.md` |
| Provider certification | `docs/scim-delta-provider-certification.md` |
| Evidence validator | `scripts/scim/validate-scim-delta-evidence.sh` |
| Checklist template | `scripts/scim/generate-scim-delta-certification-evidence-template.sh` |
| Smoke alignment | `scripts/scim/scim-delta-remote-fetch-smoke.sh` |
| CI fixtures | `scripts/scim/ci-scim-delta-remote-fetch-fixtures.sh` |

## Non-goals

- Production scheduler
- Provider mutation / non-GET
- Checkpoint-based production sync
- Live sandbox execution in CI (fixtures only)

Detay: [`phase-120-summary.md`](phase-120-summary.md).
