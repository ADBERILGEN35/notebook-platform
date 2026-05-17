# Faz 134 Özeti: Staging PP Secrets Governance + Rotation Runbook

Önceki faz: [phase-133-summary.md](phase-133-summary.md). Faz spec: [phase-134.md](phase-134.md).

## 1. Yapılanlar

PP-1/PP-2 staging secrets için governance ve rotation runbook eklendi; probe’a `--print-required` katalog modu eklendi. **Production flag açılmadı.**

| Deliverable | Path |
|-------------|------|
| Governance runbook | [backend-staging-pp-secrets-governance.md](../backend-staging-pp-secrets-governance.md) |
| Probe catalog | `check-staging-pp-secrets.sh --print-required` |
| Doc cross-links | setup, execution plan, threat model, production-readiness, readiness review |

## 2. Governance kapsamı (secret isimleri — değer yok)

| Secret / asset | PP | Owner (özet) | TTL / rotation özeti |
|----------------|-----|--------------|----------------------|
| `SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL` | PP-1 | Platform | URL değişince güncelle |
| `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN` | PP-1 | Identity + security | ≤24h; run sonrası revoke/rotate |
| `SCIM_DELTA_SANDBOX_PROVIDER` | PP-1 | Integration | Variable (non-secret) |
| `SCIM_DELTA_SANDBOX_EXPECT_READY` | PP-1 | Release manager | Variable |
| IdP `scimDeltaRemoteFetch` bearer (ESO/Vault) | PP-1 | Integration | Sandbox; run sonrası rotate |
| `BREAK_GLASS_STAGING_API_BASE_URL` | PP-2 | Platform | Staging URL |
| `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN` | PP-2 | Security | ≤8h; drill sonrası revoke |
| `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` | PP-2 | Security | En kısa TTL; drill sonrası rotate |
| `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` | PP-2 | Security | Drill sonrası revoke + rotate |
| `BREAK_GLASS_STAGING_TEST_ENDPOINT` | PP-2 | Platform | Variable |

## 3. Governance kararları

- GitHub **Environment `staging`** secrets tercih edilir.
- Live workflow: **manual dispatch only** (break-glass); SCIM sandbox staging push veya dispatch.
- SCIM admin + IdP bearer: **sandbox-only**, dry-run, **rotate-after-run**.
- Break-glass emergency: **shortest TTL**, **mandatory rotate-after-drill**.
- Secret değerleri dokümana, örneklere veya repoya **yazılmaz**.

## 4. Live workflow durumu

| Action | Durum |
|--------|--------|
| PP-1 / PP-2 workflow dispatch | **NOT_RUN** (Faz 133 ile aynı — secrets missing) |
| Post-run cleanup | N/A (live run yok) |

## 5. Production flag durumu

**Production flag açılmadı.** Runtime business behavior değişmedi.

## 6. Privacy / check-no-secrets

| Kontrol | Sonuç |
|---------|--------|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `--print-required` / `--json` output | Secret **değeri yok**; yalnızca isim + açıklama |

## 7. Post-run cleanup (operatör referansı)

Governance doc § Post-run cleanup: admin revoke, emergency token rotate, IdP bearer rotate, GitHub secret delete/rotate, artifact privacy validation.

## 8. Komut tablosu

| Komut | Sonuç |
|-------|--------|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/security/check-staging-pp-secrets.sh --print-required` | **PASS** |
| `bash scripts/security/check-staging-pp-secrets.sh --json` | **PASS** (exit 1, safe JSON) |
| `bash scripts/security/ci-check-staging-pp-secrets.sh` | **PASS** |
| `bash scripts/security/ci-download-backend-pp-artifacts.sh` | **PASS** |
| `bash scripts/security/ci-run-backend-live-staging-pp-evidence.sh` | **PASS** |
| `bash -n scripts/security/*.sh` | **PASS** |

## 9. Sonraki adım

1. Security/platform: `staging` environment secrets oluştur (`gh secret set … --env staging --body-file -`).
2. `check-staging-pp-secrets.sh --check-github` → `READY_GITHUB`.
3. Dispatch + download + bundle (Faz 133).
4. Post-run cleanup checklist (bu faz governance doc).
5. Bundle **GO** → RC sign-off güncelle.
