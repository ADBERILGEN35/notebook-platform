# Faz 145: Frontend E2E + Responsive + Accessibility Polish

## 1. Yapılanlar

Mevcut kullanıcı ve admin ekranları için responsive polish, accessibility baseline, Playwright E2E smoke ve route regression güçlendirmesi. **Yeni büyük product feature yok.** **Backend değişikliği yok.**

Önceki: [phase-144-summary.md](phase-144-summary.md).

## 2. Responsive polish

- Auth: `AuthShell` mobile padding, `#auth-main` landmark
- App shell: `SkipToMain`, `overflow-x-hidden`, mobile drawer (mevcut)
- Admin tables: `ResponsiveTableShell` (change requests, dead-letter)
- GitOps diff: side-by-side gizli `md` altında; unified her zaman
- Global search overlay: mobile-safe dialog

## 3. Accessibility

- Global `:focus-visible` ring (`index.css`)
- `SkipToMain` (auth + app shell)
- `Modal`: Escape, focus on open, `aria-label` close
- `ResponsiveTableShell`: `role="region"` + `aria-label`
- `GlobalSearchOverlay`: `data-testid`, mevcut dialog semantics

## 4. E2E smoke (`frontend/tests/e2e/`)

| Dosya | Kapsam |
|-------|--------|
| `auth.spec.ts` | Login/signup render, skip link |
| `app-shell.spec.ts` | Hub, note editor route, mobile drawer |
| `search.spec.ts` | Ctrl+K overlay, settings |
| `admin.spec.ts` | Admin overview, retention hub mobile |
| `change-requests.spec.ts` | Liste, GitOps diff (session dry-run) |
| `retention-notifications.spec.ts` | Analytics, dead-letter, retention, platform |

Fixtures: `helpers/stub-smoke-admin.ts`, `helpers/no-secrets.ts`. Legacy `frontend/e2e/` korunur.

## 5. Vitest

`phase-145-a11y.test.tsx` — SkipToMain, ResponsiveTableShell, Modal.

## 6. Kurallar

- Production mock veri yok; E2E API route mock
- Production feature flag açılmadı
- `assertNoSecretsVisible` regression

## 7. Çalıştırma

```bash
cd frontend && npm test
cd frontend && npx tsc -b
cd frontend && npm run test:e2e -- tests/e2e
```

## 8. Sonraki

CI Playwright job; axe-core optional pass; visual regression (optional).
