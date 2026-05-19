# Faz 146 Özeti: Frontend Release Candidate CI Gate + Evidence Package

Spec: [phase-146.md](phase-146.md). Önceki: [phase-145-summary.md](phase-145-summary.md).

## 1. Yapılanlar

Tek frontend release-candidate gate: secret scan, Vitest, `tsc -b`, Playwright `tests/e2e` (chromium), `npm run build`. Sanitized evidence JSON + Markdown ve GitHub Actions workflow. Yeni product feature yok.

| Bileşen | Dosya |
|---------|--------|
| Orchestrator | `scripts/security/ci-frontend-rc-readiness.sh` |
| Report builder | `scripts/security/build_frontend_rc_readiness_report.py` |
| CI workflow | `.github/workflows/frontend-rc-readiness.yml` |

## 2. CI gate kontrolleri

| Check ID | Komut / davranış | Failure category |
|----------|------------------|------------------|
| `check-no-secrets` | `scripts/check-no-secrets.sh` | `SECRET_FAILURE` |
| `vitest-unit` | `cd frontend && npm test` | `UNIT_TEST_FAILURE` |
| `typescript-build` | `cd frontend && npx tsc -b` | `TYPESCRIPT_FAILURE` |
| `playwright-smoke` | `npx playwright test tests/e2e --project=chromium` | `PLAYWRIGHT_FAILURE` / `ENVIRONMENT_SKIPPED` |
| `frontend-production-build` | `cd frontend && npm run build` (`tsc -b && vite build`) | `BUILD_FAILURE` |

## 3. Verdict ve environment skip

**Frontend RC gate verdict (Node.js 22 doğrulama): `PASS`**

| Check | Status |
|-------|--------|
| `check-no-secrets` | pass |
| `vitest-unit` | pass (234 tests) |
| `typescript-build` | pass |
| `playwright-smoke` | pass (15/15 chromium) |
| `frontend-production-build` | pass |

**Environment skip:** Bu fazda tam orchestrator çalıştırmasında Playwright skip yok (Windows Node 22 ile tüm kontroller geçti). WSL’de eski Node ile `bash scripts/security/ci-frontend-rc-readiness.sh` çalıştırılırsa vitest/tsc fail olur — script artık Node 20+ zorunlu kılar. PR workflow’da `RC_SKIP_PLAYWRIGHT=true` ile Playwright **bilinçli skip** → verdict `PASS_WITH_ENVIRONMENT_SKIPS` (diğer kontroller geçerse). `main` / `workflow_dispatch`: Playwright chromium kurulur, skip yok.

Final verdict değerleri: `PASS` | `PASS_WITH_ENVIRONMENT_SKIPS` | `FAIL`.

## 4. Evidence artifacts

| Artifact | Açıklama |
|----------|----------|
| `frontend-rc-readiness-out/frontend-rc-readiness-results.json` | Sanitized machine-readable bundle (`frontend-rc-readiness-v1`) |
| `frontend-rc-readiness-out/frontend-rc-readiness-summary.md` | Release ticket için Markdown özet |
| GitHub Actions artifact `frontend-rc-readiness` | Yukarıdaki iki dosya (`if: always()`) |
| `GITHUB_STEP_SUMMARY` | Check tablosu + route counts (workflow + report builder) |

Route inventory (router’dan): auth, app shell, workspace/notes, search/settings, admin — `build_frontend_rc_readiness_report.py` içinde kategorize edilir (~51 route).

## 5. Workflow davranışı

- **Tetikleyiciler:** `push` → `main`, `pull_request` (frontend + script paths), `workflow_dispatch`
- **Node:** 22, `npm ci`, Playwright `chromium` install (skip yalnız PR veya dispatch `skip_playwright`)
- **Env:** `RC_ALLOW_PLAYWRIGHT_SKIP=false` on GHA; `GITHUB_ACTIONS` iken Playwright skip edilmez
- **Artifact upload:** `if: always()`

## 6. Backend / production

| Kural | Durum |
|-------|--------|
| Backend API değişikliği | **Yok** |
| Yeni backend endpoint | **Yok** |
| Production feature flag açılması | **Yok** |
| Yeni büyük UI feature | **Yok** |
| AdminGate bypass | **Yok** |
| Secret/PII in artifacts | **Yok** (sanitized output) |

## 7. Test sonuçları (validation)

| Komut | Sonuç |
|-------|--------|
| `bash -n scripts/security/ci-frontend-rc-readiness.sh` | **PASS** |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `cd frontend && npm test` | **PASS** (234) |
| `cd frontend && npx tsc -b` | **PASS** |
| `cd frontend && npx playwright test tests/e2e --project=chromium` | **PASS** (15/15) |
| `cd frontend && npm run build` | **PASS** |

Node: v22.22.0 (Windows). Orchestrator tam bash run: WSL eski Node ortamında fail — CI ve Node 20+ local PATH ile kullanın.

## 8. Dokümantasyon

- `docs/phases/phase-146.md`
- `docs/frontend-implementation-plan.md` — Faz 146 satırı + RC gate bölümü
- `docs/frontend-design-system.md` — RC gate tablosu

## 9. Sonraki adımlar

1. Release ticket şablonuna `frontend-rc-readiness-summary.md` ekleme.
2. `main` üzerinde ilk GHA run ile artifact doğrulama.
3. Opsiyonel: axe-core job; legacy `frontend/e2e/` full suite ayrı job.
