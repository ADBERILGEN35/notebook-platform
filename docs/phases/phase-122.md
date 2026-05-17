# Faz 122: Break-glass Active Token Revocation / Denylist

## Hedef

Break-glass JWT’ler için aktif revocation/denylist foundation: admin security ekranından session listeleme ve anında geçersiz kılma; gateway denylist kontrolü.

## Scope

| Katman | Deliverable |
|--------|-------------|
| Identity | `BreakGlassSessionService`, `/internal/admin/break-glass/sessions/*`, metrics, cleanup job |
| Gateway | `/admin/security/break-glass/sessions/*`, denylist filter metrics |
| Frontend | Enterprise Security break-glass active sessions panel |
| DB | `V21` — `issued_at` on denylist |

## Non-goals

Production scheduler, normal access-token denylist, provider mutation, break-glass login contract changes.

Detay: [`phase-122-summary.md`](phase-122-summary.md).
