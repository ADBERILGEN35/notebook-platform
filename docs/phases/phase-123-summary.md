# Faz 123 Özeti: Break-glass Revocation Staging Drill + Evidence Gate

Önceki faz: [phase-122-summary.md](phase-122-summary.md). Faz spec: [phase-123.md](phase-123.md).

## 1. Yapılanlar

Staging break-glass revocation drill paketi: bash drill + HTTP helper (token loglamaz), evidence validator/report, CI fixtures, GitHub Actions workflow (`workflow_dispatch` live drill), runbook + manual UI checklist. **Production defaultları ve backend/frontend runtime kodu değişmedi.**

### Net sınırlar

| Soru | Cevap |
|------|--------|
| Production flag değişti mi? | **Hayır** |
| Live staging drill (bu ortam) | **Çalıştırılmadı** — staging secrets yok |
| Normal token denylist? | **Hayır** |
| Token/JWT artifact’te? | **Hayır** |

## 2. Drill akışı

1. Break-glass login (emergency token) veya mevcut break-glass JWT (env).
2. `GET /admin/security/break-glass/sessions` (admin token).
3. `POST .../sessions/{revokeRef}/revoke` (reason ≥ 10 chars).
4. Denylist cache bekleme (default 35s).
5. Break-glass JWT ile test endpoint → beklenen **401** + **BREAK_GLASS_TOKEN_REVOKED**.

## 3. Artifact’lar

| Dosya | İçerik |
|-------|--------|
| `break-glass-revocation-evidence.json` | Sanitized drill sonucu |
| `break-glass-revocation-summary.md` | CR / Step Summary özeti |

Upload (GHA): `break-glass-revocation-evidence`.

## 4. Gateway denylist reject evidence

Evidence alanları: `gatewayRejectStatus` (401), `gatewayRejectErrorCode` (`BREAK_GLASS_TOKEN_REVOKED`), `jtiMasked`, `sessionIdShort`, `revokeRefMasked`, `revokedAt`, `revokedByPresent`, `reasonPresent`.

## 5. Skip / exit

| Durum | result | Exit |
|-------|--------|------|
| Secret yok | skipped | 0 |
| Başarılı zincir | passed | 0 |
| Readiness gap | readiness-gap | 2 |
| Privacy leak | privacy-failure | 3 |
| Shape hatası | shape-mismatch | 4 |
| Revoked token kabul | failed | 5 |

## 6. GitHub secrets (dokümante)

`BREAK_GLASS_STAGING_API_BASE_URL`, `BREAK_GLASS_STAGING_ADMIN_ACCESS_TOKEN`, `BREAK_GLASS_STAGING_EMERGENCY_TOKEN` veya `BREAK_GLASS_STAGING_BREAK_GLASS_TOKEN`, opsiyonel `BREAK_GLASS_STAGING_TEST_ENDPOINT`.

## 7. Tests / doğrulama

| Komut | Sonuç |
|-------|--------|
| `bash scripts/security/ci-break-glass-revocation-fixtures.sh` | **PASS** |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `./gradlew :identity-service:test --tests "*BreakGlass*"` | **PASS** |
| `./gradlew :api-gateway:test --tests "*BreakGlass*"` | **PASS** |
| Frontend vitest/tsc | **SKIP** (toolchain) |

## 8. Privacy

Script token’ı temp dosyada tutar; stdout’a yazmaz. Evidence validator forbidden pattern grep. Emergency token ve raw JWT artifact’e girmez.

## 9. Sonraki adım

1. Staging’de prerequisite flag’leri aç (kontrollü).
2. `workflow_dispatch` drill → artifact’leri CR’ye ekle.
3. Manual UI checklist tamamla → production denylist enforcement onayı.
