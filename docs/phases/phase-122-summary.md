# Faz 122 Özeti: Break-glass Active Token Revocation / Denylist

Önceki faz: [phase-121-summary.md](phase-121-summary.md). Faz spec: [phase-122.md](phase-122.md).

## 1. Yapılanlar

Break-glass session revocation foundation: jti denylist (mevcut tablo genişletildi), session list/revoke/revoke-all API’leri, gateway security proxy, denylist filter metrics, Enterprise Security UI paneli, audit + Micrometer metrics, opsiyonel denylist cleanup job.

### Net sınırlar

| Soru | Cevap |
|------|--------|
| Normal access token denylist? | **Hayır** — yalnız break-glass |
| Token/JWT UI’da? | **Hayır** — masked jti + kısa session id |
| MFA/admin-write gevşetildi mi? | **Hayır** — revoke `ensureBreakGlassRevoke` + MFA |
| Gateway write block default? | **Değişmedi** |

## 2. Revocation modeli

1. Break-glass login JWT’ye `jti` + `break_glass_session_id` claim’leri eklenir (Faz 93+).
2. Revoke: `break_glass_token_denylist` satırı (`jti`, `issued_at`, `expires_at`, `revoked_at`, `revoked_by`, `reason`, `source`).
3. Gateway: `BreakGlassDenylistClient` → `GET /internal/break-glass/tokens/{jti}/revoked` (TTL cache, default 30s).
4. Revoked jti → `401` + `BREAK_GLASS_TOKEN_REVOKED` (expiry beklemeden).

**Source değerleri:** `ADMIN_REVOKE`, `ROTATION`, `EMERGENCY_DISABLE`, `REVIEW_REJECT`.

## 3. API’ler

| Katman | Endpoint |
|--------|----------|
| Identity | `GET/POST /internal/admin/break-glass/sessions` (+ `{ref}/revoke`, `revoke-all-active`) |
| Gateway | `GET/POST /admin/security/break-glass/sessions` (aynı semantik) |

`{ref}`: session id veya internal jti (ham token değil).

## 4. Denylist kontrolü (gateway)

`BreakGlassGuardWebFilter`: break-glass claim varsa → denylist check (flag: `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED`) → revoked ise reject + metric `break_glass_revoked_token_denied_total`.

## 5. Tests / doğrulama

| Komut | Sonuç |
|-------|--------|
| `./gradlew :identity-service:test --tests "*BreakGlass*"` | **PASS** |
| `./gradlew :api-gateway:test --tests "*BreakGlass*"` | **PASS** |
| `cd frontend && npm test -- --run BreakGlassActiveSessions` | **SKIP** — `vitest` PATH’te yok (Windows) |
| `cd frontend && npx tsc -b` | **SKIP** — `tsc` PATH’te yok (Windows) |
| `bash scripts/check-no-secrets.sh` | **PASS** |

## 6. Privacy

- Response/log/audit: token, raw JWT, bearer, tam jti yok.
- Liste: `jtiMasked`, kısa `sessionId`, revoke için opaque `revokeRef` (session id).

## 7. MFA / admin-write

Revoke ve revoke-all: `PERM_BREAK_GLASS_REVOKE` + gateway `ADMIN_WRITE_MFA_REQUIRED` when configured. Read list: `PERM_BREAK_GLASS_READ` only.

## 8. Config

- `BREAK_GLASS_REVOCATION_ENABLED` (identity, default false)
- `BREAK_GLASS_REVOCATION_CACHE_TTL_SECONDS` / gateway `GATEWAY_BREAK_GLASS_DENYLIST_CACHE_SECONDS` (30)
- `BREAK_GLASS_REVOCATION_CLEANUP_ENABLED` (false)
- `GATEWAY_BREAK_GLASS_DENYLIST_CHECK_ENABLED` (false)
- `FRONTEND_BREAK_GLASS_REVOCATION_UI_ENABLED`

## 9. Sonraki adım

1. Staging’de revocation + denylist check flag’lerini kontrollü aç.
2. Break-glass drill: issue → list → revoke → gateway reject doğrula.
3. SCIM scheduler fazına geçmeden önce canlı sandbox evidence (Faz 121) tamamla.
