# Faz 135: Staging PP Evidence Live Run Orchestrator Finalization

## Hedef

Tek orchestrator ile probe → dispatch → download → validate → bundle; standart `pp-input/` layout. **Production flag açma yok.**

Önceki faz: [phase-134-summary.md](phase-134-summary.md).

## Scope

| # | Deliverable |
|---|-------------|
| 1 | `run-backend-live-staging-pp-evidence.sh` modları: `--probe-only`, `--dispatch-github`, `--download-artifacts`, `--build-bundle`, `--all` |
| 2 | `pp-input/{scim,breakglass,retention}/` layout |
| 3 | `download-backend-pp-artifacts.sh` + manifest + `--wait` |
| 4 | Doc güncellemeleri |
| 5 | [phase-135-summary.md](phase-135-summary.md) |

## Kesin kurallar (değişmedi)

- Production flag açma yok; secret değerlerini loglama yok.
- `MISSING_SECRET` → dispatch yok; eksik artifact → **NO_GO**.

Detay: [phase-135-summary.md](phase-135-summary.md).
