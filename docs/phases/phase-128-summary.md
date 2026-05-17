# Faz 128 Özeti: Backend Docker/Testcontainers CI + Full Gradle Check Gate

Önceki faz: [phase-127-summary.md](phase-127-summary.md). Faz spec: [phase-128.md](phase-128.md).

## 1. Yapılanlar

Docker/Testcontainers ve **full Gradle** doğrulaması için Faz 127 RC gate’inden ayrılmış ağır CI kapısı eklendi. **Production flag açılmadı**; runtime feature değişmedi.

| Çıktı | Path |
|-------|------|
| Docker CI script | `scripts/security/ci-backend-docker-check.sh` |
| Report builder | `scripts/security/build_backend_docker_ci_report.py` |
| Workflow | `.github/workflows/backend-docker-ci.yml` |
| Artifacts | `backend-docker-ci-results.json`, `backend-docker-ci-summary.md` |

## 2. Faz 127 RC gate vs Docker CI

| | **RC gate (127)** | **Docker CI (128)** |
|---|-------------------|---------------------|
| Script | `ci-backend-rc-readiness.sh` | `ci-backend-docker-check.sh` |
| Docker | Opsiyonel / skip | **CI’da zorunlu** |
| Gradle | Targeted test filtreleri | `check` + `rlsIntegrationTest` (varsayılan) |
| Süre | Dakikalar | 15–45+ dk (cache’e bağlı) |
| PR | Lightweight (Gradle skip) | **Varsayılan zorunlu değil** |
| Amaç | Hızlı RC / merge güveni | Tam ortam doğrulaması |

## 3. Docker CI gate sonucu (bu ortam)

| Verdict | Koşul |
|---------|--------|
| **`FAIL`** (local tam koşu) | Docker **var**; `docker-version` / `testcontainers-sanity` **PASS**; `gradle-full-check` **FAIL** (`:api-gateway:spotlessJavaCheck` — önceden var olan format ihlali, Faz 128 kapsamı dışı) |
| **`ENVIRONMENT_SKIPPED`** | `BACKEND_DOCKER_ALLOW_SKIP=true` ve Docker yok (doğrulandı) |

**Full Gradle check çalıştı mı?** Evet — `./gradlew check rlsIntegrationTest` başlatıldı; `check` aşamasında spotless nedeniyle durdu (Testcontainers testlerine tam ulaşmadan).

## 4. GitHub Actions beklenen davranış

| Event | Davranış |
|-------|----------|
| `push` → `main` | Docker kurulu runner; gate **FAIL** spotless düzeltilene kadar / **PASS** temiz tree’de |
| `workflow_dispatch` | Aynı; `gradle_tasks` input ile görev özelleştirme |
| PR | Varsayılan workflow **tetiklenmez** (ağır gate PR blocker değil) |

Artifact: `backend-docker-ci` (`results.json` + `summary.md`), `if: always()`.

## 5. Kontrol tablosu (gate tasarımı)

| Check | Kategori | CI Docker yok |
|-------|----------|----------------|
| `docker-version` | DOCKER_UNAVAILABLE | **FAIL** |
| `docker-info` | DOCKER_UNAVAILABLE | **FAIL** |
| `testcontainers-sanity` | TESTCONTAINERS_FAILURE | **FAIL** (veya önceki adımda durur) |
| `gradle-full-check` | GRADLE_CHECK_FAILURE | atlanır |

## 6. Environment skip

| Mod | Sonuç |
|-----|--------|
| `BACKEND_DOCKER_ALLOW_SKIP=true` + Docker yok + not CI required | `ENVIRONMENT_SKIPPED`, exit 0 |
| `GITHUB_ACTIONS=true` (`BACKEND_DOCKER_CI_REQUIRED=true`) | Docker yok → **FAIL** |

## 7. Production flag durumu

**Production flag açılmadı.** Bu gate yalnızca test/CI doğrulamasıdır.

## 8. Tests / doğrulama

| Komut | Sonuç |
|-------|--------|
| `bash -n scripts/security/ci-backend-docker-check.sh` | **PASS** |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `BACKEND_DOCKER_ALLOW_SKIP=true` (Docker yok) | **ENVIRONMENT_SKIPPED** |
| `BACKEND_DOCKER_ALLOW_SKIP=true` (Docker var, local) | **FAIL** (spotless) |
| `bash scripts/security/ci-backend-rc-readiness.sh` (`RC_SKIP_HELM`) | **PASS_WITH_ENVIRONMENT_SKIPS** (Faz 127 regresyon yok) |
| Workflow YAML | `.github/workflows/backend-docker-ci.yml` — `main` + `workflow_dispatch`, timeout 90m |

## 9. Sonraki adım

1. ~~Spotless fix~~ → **Faz 129** tamamlandı; Docker CI **PASS**.
2. Staging PP evidence + bundle `GO` (Faz 125).
3. Flag flip CR (Faz 126).
