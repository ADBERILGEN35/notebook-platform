# Faz 151 Özeti: Staging Environment Bootstrap Plan for Backend PP Evidence

Spec: [phase-151.md](phase-151.md). Önceki: [phase-150-summary.md](phase-150-summary.md).

## 1. Yapılanlar

Staging ortamı için minimum bootstrap planı: altyapı gereksinimleri, PP servis akışları, GitHub secret mapping, bring-up sırası (16 adım), smoke checklist, “staging yokken çalıştırma” kuralları. Mevcut release dokümanlarına cross-link. **Backend API değişikliği yok.** **Frontend feature yok.** **Production flag açılmadı.**

| Deliverable | Dosya |
|-------------|--------|
| Bootstrap plan | [staging-environment-bootstrap-plan.md](../staging-environment-bootstrap-plan.md) |

## 2. Staging gereksinimleri (özet)

| Kategori | Gereksinim |
|----------|------------|
| Network | Staging domain, public HTTPS gateway URL, TLS, runner egress |
| Services | api-gateway, identity, workspace, content, notification, search |
| Data | PostgreSQL, Redis, Kafka (chart’a göre) |
| GitOps | Staging cluster + RC image deploy |
| Frontend | Staging SPA veya preview URL (önerilir, PP için zorunlu değil) |
| IdP | SCIM sandbox bearer via Vault/ExternalSecret (cluster) |

## 3. GitHub secrets mapping (isimler)

| Secret | Kaynak |
|--------|--------|
| `SCIM_DELTA_SANDBOX_GATEWAY_BASE_URL` | Staging gateway DNS |
| `SCIM_DELTA_SANDBOX_ADMIN_ACCESS_TOKEN` | Staging admin auth |
| `BREAK_GLASS_STAGING_API_BASE_URL` | Aynı gateway |
| `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN` | Staging admin auth |
| `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN` veya `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` | Break-glass issuance (biri zorunlu) |

Opsiyonel değişkenler: `SCIM_DELTA_SANDBOX_PROVIDER`, `BREAK_GLASS_STAGING_TEST_ENDPOINT`, vb. — [backend-staging-pp-secrets-setup.md](../backend-staging-pp-secrets-setup.md).

## 4. Bring-up sırası (özet)

1. Infra → 2. DNS/TLS → 3. DB/Redis/Kafka → 4. Backend deploy → 5. Gateway health → 6. Frontend (opsiyonel) → 7–8. Admin bootstrap + token → 9–10. Break-glass → 11. SCIM sandbox → 12. GitHub secrets → 13. PP-1 → 14. PP-2 → 15. Bundle → 16. Sign-off.

## 5. PP-1 / PP-2 live evidence koşulları

| PP | Geçiş |
|----|--------|
| PP-1 | `certificationResult: certified` |
| PP-2 | `passed` + gateway `401` + `BREAK_GLASS_TOKEN_REVOKED` |
| PP-3 | `not_required` (varsayılan) veya retention smoke pass |
| Bundle | `finalRecommendation: GO` |

**Staging yokken:** PP workflow, bundle GO, backend GO, platform GO, prod flag CR — **yapılmaz**.

## 6. Karar durumu

| Alan | Karar |
|------|--------|
| **Frontend** | **GO_WITH_ACCEPTED_RISKS** (Faz 149; değişmedi) |
| **Backend** | **NO_GO** — staging + live PP evidence eksik |
| **Platform final decision** | **NO_GO** |
| **Production flags** | **Açılmadı** |

## 7. Test / validation

| Komut | Sonuç |
|-------|--------|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `check-staging-pp-secrets.sh --print-required` | **PASS** |
| `check-staging-pp-secrets.sh --check-github` | **MISSING** (beklenen — staging secrets yok) |
| `ci-generate-platform-rc-signoff-template.sh` | **PASS** |
| `ci-generate-frontend-rc-signoff-template.sh` | **PASS** |
| `ci-backend-preprod-evidence-bundle.sh` | **PASS** (fixture senaryoları) |

## 8. Dokümantasyon güncellemeleri

- `platform-release-candidate-signoff.md`
- `platform-release-go-no-go-checklist.md`
- `backend-staging-pp-secrets-governance.md`
- `backend-live-staging-pp-evidence-run-checklist.md`
- `production-readiness.md`

## 9. Sonraki adım

Operatör: [staging-environment-bootstrap-plan.md](../staging-environment-bootstrap-plan.md) adımlarını uygula → GitHub Environment `staging` secrets → PP-1/PP-2 dispatch → bundle GO → backend/platform sign-off güncelle.
