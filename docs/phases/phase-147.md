# Faz 147: Frontend Release Sign-off + Visual QA Checklist

## 1. Yapılanlar

Frontend RC için imzalanabilir sign-off paketi ve manuel visual QA checklist. Faz 146 RC gate artifact’lerini, route inventory’yi, domain durumlarını ve GO/NO_GO kurallarını tek yerde toplar. **Backend değişikliği yok.** **Yeni UI feature yok.** **Production flag açılmaz.**

Önceki: [phase-146-summary.md](phase-146-summary.md).

## 2. Dokümanlar

| Dosya | Rol |
|-------|-----|
| [frontend-release-candidate-signoff.md](../frontend-release-candidate-signoff.md) | Sign-off spec + karar kuralları |
| [frontend-release-visual-qa-checklist.md](../frontend-release-visual-qa-checklist.md) | Manuel QA (varsayılan unchecked) |
| `generate-frontend-rc-signoff-template.sh` | Placeholder sign-off üretici |
| `ci-generate-frontend-rc-signoff-template.sh` | Template + forbidden pattern CI |

## 3. Sign-off paketi bölümleri

RC ID, SHA/build artifact, RC gate verdict, vitest/tsc/playwright/build, route inventory, User/Admin domain, responsive/a11y, limitations, risks, final decision, approvers.

## 4. Karar kuralları (özet)

| Durum | Karar |
|-------|--------|
| RC PASS + visual QA + no blocker | GO |
| RC PASS + accepted visual limits | GO_WITH_ACCEPTED_RISKS |
| RC FAIL / Playwright fail / TS fail / secret leak / major route broken | NO_GO |

## 5. Visual QA kapsamı

Auth, AppShell/hub, note editor, search/settings/sync, admin/identity/break-glass, change requests/GitOps diff, retention/notification ops, mobile/tablet/desktop, keyboard/focus/dialogs, no secrets in UI.

## 6. Beklenen artifact’ler

- `frontend-rc-readiness-summary.md`
- `frontend-rc-readiness-results.json`
- Completed visual QA checklist
- Optional: sign-off markdown from template generator

## 7. Çalıştırma

```bash
bash scripts/security/generate-frontend-rc-signoff-template.sh --rc-id fe-rc-YYYY-MM-DD --output signoff.md
bash scripts/security/ci-generate-frontend-rc-signoff-template.sh
```

## 8. Sonraki

Release ticket’a sign-off + checklist ekleme; ilk production frontend promotion CR (backend sign-off ile).
