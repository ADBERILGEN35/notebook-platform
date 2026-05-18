# Faz 138: Frontend Auth Foundation

## 1. Yapılanlar

Stitch auth tasarımları (login, register, forgot password, MFA, SSO callback) React/Vite/TypeScript component mimarisine taşındı. Ortak `AuthShell` / `AuthCard` / input bileşenleri, beş public route ve vitest kapsamı eklendi. **Backend değiştirilmedi.**

Önceki bağlam: [phase-137-summary.md](phase-137-summary.md) (backend sign-off beklemede).

## 2. Backend değişiklikleri

Yok. Mevcut `auth-api.ts`, `mfa-api.ts` ve SSO authorize URL pattern korundu.

## 3. Frontend değişiklikleri

| Alan | Değişiklik |
|------|------------|
| Auth UI kit | `frontend/src/features/auth/components/*` |
| Sayfalar | `LoginPage`, `RegisterPage`, `ForgotPasswordPage`, `MfaAuthenticationPage`, `SsoCallbackPage` |
| Router | `frontend/src/app/router.tsx` — public auth routes |
| Tasarım tokenları | `tailwind.config.ts` — design system renkleri |
| MFA akışı | Login → `sessionStorage` mfa session → `/mfa` |
| SSO | Authorize `returnUrl=/sso/callback`; callback `me()` ile session doğrulama |

## 4. Security/Privacy

- Token/JWT/SSO code/MFA secret UI’da gösterilmez.
- SSO hata kodları kullanıcıya güvenli mesajla map edilir (`sso-errors.ts`).
- Forgot password: backend endpoint yok — yalnızca client acknowledgment (PII log yok).

## 5. Tests

| Komut | Durum (review ortamı) |
|-------|------------------------|
| `npm test -- --run Auth` | **Çalıştırılamadı** — `vitest`/`tsc` PATH (environment) |
| Eklenen test dosyaları | `auth-pages.test.tsx`, `LoginPage.test.tsx`, `AuthInput.test.tsx`, `password-strength.test.ts`, `router.auth.test.tsx` |

## 6. Config/Deployment

- Google Fonts (Inter, Manrope) `index.html` link — Tailwind/Material CDN script yok.
- `/signup` → `SignupPage` re-export (`RegisterPage`).

## 7. Eklenen/düzenlenen dosyalar

Detay: [phase-138-summary.md](phase-138-summary.md).

## 8. Kalan açıklar

- Password reset backend API bağlantısı (Faz sonrası).
- TOTP doğrulama endpoint’i yok — MFA sayfası passkey + recovery code.
- Register SSO `generic-oidc` sabit; production IdP listesi ile hizalanmalı.

## 9. Sonraki faz önerileri

1. App shell / workspace UI (post-auth).
2. Password reset API entegrasyonu.
3. E2E auth flows (`frontend/e2e/auth.spec.ts` güncelle).
