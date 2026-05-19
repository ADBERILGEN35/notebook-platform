# Faz 146: Frontend Release Candidate CI Gate + Evidence Package

## 1. Yapılanlar

Tek frontend RC gate: secret scan, vitest, `tsc -b`, Playwright `tests/e2e` smoke, `npm run build`. Sanitized JSON + Markdown evidence. **Backend değişikliği yok.** **Yeni UI feature yok.**

Önceki: [phase-145-summary.md](phase-145-summary.md).

## 2. Script ve workflow

| Artifact | Açıklama |
|----------|----------|
| `scripts/security/ci-frontend-rc-readiness.sh` | Orchestrator |
| `scripts/security/build_frontend_rc_readiness_report.py` | Report + route inventory |
| `.github/workflows/frontend-rc-readiness.yml` | CI (main push, PR, dispatch) |

## 3. Checks

| ID | Category |
|----|----------|
| `check-no-secrets` | SECRET_FAILURE |
| `vitest-unit` | UNIT_TEST_FAILURE |
| `typescript-build` | TYPESCRIPT_FAILURE |
| `playwright-smoke` | PLAYWRIGHT_FAILURE / ENVIRONMENT_SKIPPED |
| `frontend-production-build` | BUILD_FAILURE |

## 4. Verdicts

- `PASS`
- `PASS_WITH_ENVIRONMENT_SKIPS` (yalnızca Playwright local skip)
- `FAIL`

## 5. Evidence outputs

- `frontend-rc-readiness-out/frontend-rc-readiness-results.json`
- `frontend-rc-readiness-out/frontend-rc-readiness-summary.md`

## 6. Kurallar

- Production feature flag açılmaz
- CI artifact’lerde secret/PII yok
- PR: `RC_SKIP_PLAYWRIGHT=true` (lightweight); main/dispatch: Playwright kurulu

## 7. Çalıştırma

```bash
bash scripts/security/ci-frontend-rc-readiness.sh
```

## 8. Sonraki

CI’da RC gate’i release ticket şablonuna bağlama; opsiyonel axe-core.
