# Faz 147 Özeti: Frontend Release Sign-off + Visual QA Checklist

Spec: [phase-147.md](phase-147.md). Önceki: [phase-146-summary.md](phase-146-summary.md).

## 1. Yapılanlar

Frontend release-candidate için imzalanabilir sign-off spec, manuel visual QA checklist (varsayılan **unchecked**), placeholder template generator ve CI forbidden-pattern scan. Yeni product feature yok.

| Bileşen | Dosya |
|---------|--------|
| Sign-off spec | `docs/frontend-release-candidate-signoff.md` |
| Visual QA | `docs/frontend-release-visual-qa-checklist.md` |
| Template | `scripts/security/generate-frontend-rc-signoff-template.sh` |
| Template CI | `scripts/security/ci-generate-frontend-rc-signoff-template.sh` |

## 2. Sign-off paketinin beklediği artifact’ler

| Artifact | Kaynak |
|----------|--------|
| `frontend-rc-readiness-summary.md` | Faz 146 RC gate |
| `frontend-rc-readiness-results.json` | Faz 146 RC gate |
| Completed `frontend-release-visual-qa-checklist.md` | Manuel QA |
| Production build reference | CI / dist hash (secret yok) |
| Optional sign-off markdown | Template generator çıktısı |

Template varsayılan **final decision: NO_GO** — operatör RC + visual QA sonrası günceller.

## 3. Visual QA checklist kapsamı

| Alan | Örnek kontroller |
|------|------------------|
| Auth | Login/register, skip link, MFA/SSO safe states |
| AppShell / Hub | Drawer, nav, workspace hub |
| Note editor | Shell, collab limit waiver |
| Search / settings / sync | Ctrl+K overlay, settings, offline diagnostics |
| Admin / identity / break-glass | Sanitized diagnostics, audit tables |
| Change requests / GitOps | Responsive tables, unified/side-by-side diff |
| Retention / notification ops | Masked DLQ, purge confirm, legal holds |
| Viewports | Mobile ≤390px, tablet, desktop |
| A11y | Keyboard, `:focus-visible`, modals, Escape |
| Global | No token/secret in DOM; AdminGate |

Checklist **GO göstermez**; `Visual QA status: INCOMPLETE` varsayılan.

## 4. Frontend RC gate durumu

Faz 146 doğrulaması (bu fazda yeniden koşuldu):

| Metrik | Değer |
|--------|--------|
| **RC gate verdict** | **PASS** |
| Vitest | 234 pass |
| TypeScript | pass |
| Playwright smoke | 15/15 (chromium, `tests/e2e`) |
| Production build | pass |
| Secret scan | pass |

Sign-off **GO** için RC PASS tek başına yeterli değil — visual QA tamamlanmalı.

## 5. Final decision kuralları (özet)

| Koşul | Karar |
|-------|--------|
| RC PASS + visual QA complete + no blocker | GO |
| RC PASS + accepted visual limitations | GO_WITH_ACCEPTED_RISKS |
| RC FAIL, Playwright/TS/Vitest/build fail, secret leak, major route broken | NO_GO |

Bu fazın deliverable’ı template/spec — operatör sign-off henüz doldurulmadığı için paket **NO_GO** (template default).

## 6. Backend / production

| Kural | Durum |
|-------|--------|
| Backend API değişikliği | **Yok** |
| Yeni backend endpoint | **Yok** |
| Production feature flag | **Açılmadı** |
| Yeni büyük UI feature | **Yok** |
| AdminGate bypass | **Yok** |

## 7. Test sonuçları (validation)

| Komut | Sonuç |
|-------|--------|
| `bash -n scripts/security/generate-frontend-rc-signoff-template.sh` | **PASS** |
| `bash scripts/security/ci-generate-frontend-rc-signoff-template.sh` | **PASS** |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `cd frontend && npm test` | **PASS** (234) |
| `cd frontend && npx tsc -b` | **PASS** |
| `cd frontend && npx playwright test tests/e2e --project=chromium` | **PASS** (15/15) |
| `cd frontend && npm run build` | **PASS** |

## 8. Bilinen limitler (dokümante)

| ID | Limit |
|----|--------|
| L1 | E2E Playwright API stubs; tam gateway entegrasyonu yok |
| L2 | axe-core / visual regression RC gate’te yok |
| L3 | Legacy `frontend/e2e/` RC smoke dışında |
| L4 | Note editor E2E yalnızca route shell |
| L5 | Admin metrikleri canlı admin API gerektirir |
| L6 | Arama relevance frontend gate dışında |
| L7 | PR RC run Playwright skip — production sign-off için `main` full gate gerekir |

## 9. Sonraki adımlar

1. Release ticket’a RC JSON/Markdown + completed visual QA ekle.
2. `generate-frontend-rc-signoff-template.sh` ile doldurulmuş sign-off kaydet.
3. Backend [backend-release-candidate-signoff.md](../backend-release-candidate-signoff.md) ile koordine et.
4. Opsiyonel: axe-core job; legacy e2e ayrı workflow.
