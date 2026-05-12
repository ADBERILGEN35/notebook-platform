# Offline data encryption (Faz 69)

Faz 69 hardens offline storage for the offline edit/sync MVP introduced in Faz 68.

## Scope

- Primary: encrypt `offline_note_drafts` sensitive payloads at rest.
- Secondary: optional encryption path for offline notes cache payloads.
- Out of scope: passphrase unlock, backend key wrapping, E2EE, hardware key storage.

## Crypto model

- Algorithm: `AES-GCM` (256-bit).
- IV: 12 bytes random per encryption.
- Key: session-bound, memory-only (`CryptoKey`, non-extractable).
- Stored payload format:
  - `version`
  - `algorithm`
  - `iv` (base64)
  - `ciphertext` (base64)

## Feature flags

- `FRONTEND_OFFLINE_ENCRYPTION_ENABLED` (default `false`)
- `FRONTEND_OFFLINE_DRAFT_ENCRYPTION_REQUIRED` (default `false`)
- `FRONTEND_OFFLINE_CACHE_ENCRYPTION_ENABLED` (default `false`)

## Key lifecycle

- Key is created during active authenticated session when offline encryption is enabled.
- Key is never persisted in `localStorage`, `sessionStorage`, or `IndexedDB`.
- `clearSession` clears both offline DB and in-memory encryption key.
- If app starts offline with encrypted data but no key, data is unavailable until a new session key exists.

## Draft/cache storage behavior

- Draft store keeps metadata in plaintext (`noteId`, `workspaceId`, `status`, timestamps).
- Sensitive fields (`baseSnapshot`, `localSnapshot`, `baseEtag`, `baseUpdatedAt`) are encrypted payload.
- If draft encryption is required and plaintext legacy rows are encountered, they are cleared.
- Cache encryption is optional and uses the same WebCrypto utility.

## Guardrails

- If offline edit is enabled **and** draft encryption is required **and** crypto/key is unavailable,
  offline edit falls back to read-only with warning.
- Settings displays encryption status and offers stale encrypted data cleanup.
- Faz 70 adds sync hardening so encrypted-but-locked drafts are surfaced as locked/unavailable states
  instead of unsafe retries.
- Faz 74 foreground background sync foundation also blocks runs when encryption key/session state is
  unavailable; locked encrypted drafts stay outside auto-safe eligibility.
- Faz 75 MVP keeps this guardrail in app lifecycle triggers and surfaces blocked state in foreground UI
  rather than attempting unsafe background retries.
- Faz 96 Service Worker Background Sync dry-run does not persist or receive the memory-only key.
  Encrypted locked drafts are skipped with `encrypted_key_unavailable`; foreground sync remains the
  safe path for encrypted drafts.

## Security caveats

- Improves at-rest local storage exposure risk.
- Does not eliminate XSS risk while key is in memory.
- Does not protect against full browser process compromise.
- Service workers cannot safely replay encrypted drafts after page close/restart without a future,
  explicitly approved unlock/key-wrapping design.
