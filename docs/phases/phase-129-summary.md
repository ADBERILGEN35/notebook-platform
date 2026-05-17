# Faz 129 Özeti: Backend Docker CI Green Fix + Release Candidate Evidence

Önceki faz: [phase-128-summary.md](phase-128-summary.md). Faz spec: [phase-129.md](phase-129.md).

## 1. Yapılanlar

Faz 128 Docker CI `FAIL` kök nedenleri giderildi: **spotless** format ihlalleri ve **Spring configuration-properties** binding. **Production flag açılmadı.**

| Değişiklik | Tür |
|------------|-----|
| `api-gateway` — `ApiGatewayApplication.java`, `AdminScimDiagnosticsControllerTest.java` | Spotless (import sırası, satır kırımı) |
| `identity-service` — `ScimProperties.java` | Binding fix: 10-arg ctor → `withLegacyDefaults` static factory |
| `identity-service` — `DefaultScimDeltaProviderClient.java` | `@Autowired` tek ctor; test ctor private + `forTest` |
| `identity-service` — çoklu test dosyaları | Spotless + `withLegacyDefaults` çağrıları |
| Readiness dokümanları | RC/Docker CI sonuçları güncellendi |

## 2. Docker CI verdict

| Verdict | Bu ortam |
|---------|----------|
| **`PASS`** | `bash scripts/security/ci-backend-docker-check.sh` — docker sanity + full `./gradlew check rlsIntegrationTest` **BUILD SUCCESSFUL** |

Faz 128: **FAIL** (`FORMAT_FAILURE` / `GRADLE_CHECK_FAILURE` spotless + context load).  
Faz 129: **PASS**.

## 3. Spotless düzeltmesi

| Modül | Durum |
|-------|--------|
| `:api-gateway:spotlessCheck` | **PASS** |
| `:identity-service:spotlessCheck` | **PASS** (spotlessApply test dosyaları dahil) |

`:api-gateway:spotlessJavaCheck` Faz 128’deki ihlal **giderildi**.

## 4. Ek düzeltmeler (GRADLE_CHECK_FAILURE)

| Sorun | Sınıf | Düzeltme |
|-------|-------|----------|
| `NoSuchMethodException` `ScimProperties.<init>()` | `EXISTING_TEST_FAILURE` / binding | 10-arg instance ctor kaldırıldı; `withLegacyDefaults` |
| `NoSuchMethodException` `DefaultScimDeltaProviderClient.<init>()` | DI wiring | `@Autowired` public ctor; package test ctor → private |

**Runtime business behavior unchanged** — yalnızca formatlama ve Spring’in bean oluşturması için ctor görünürlüğü.

## 5. RC gate ve secrets

| Komut | Sonuç |
|-------|--------|
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/security/ci-backend-rc-readiness.sh` (`RC_SKIP_HELM=true`) | **PASS_WITH_ENVIRONMENT_SKIPS** |

## 6. Production flag durumu

**Production flag açılmadı.** Chart/GitOps dangerous defaults değişmedi.

## 7. Release candidate evidence

| Artifact | Path |
|----------|------|
| Docker CI JSON | `backend-docker-ci-out/backend-docker-ci-results.json` |
| Docker CI summary | `backend-docker-ci-out/backend-docker-ci-summary.md` |
| RC summary | `backend-rc-readiness-out/backend-rc-readiness-summary.md` |

Pre-prod bundle (PP-1..PP-3 live staging) hâlâ operasyonel adım — Faz 125.

## 8. Komut tablosu

| Komut | Sonuç |
|-------|--------|
| `./gradlew :api-gateway:spotlessApply --no-build-cache --no-configuration-cache` | **PASS** |
| `./gradlew :api-gateway:spotlessCheck --no-build-cache --no-configuration-cache` | **PASS** (bash/WSL) |
| `./gradlew :identity-service:spotlessApply` | **PASS** |
| `bash scripts/check-no-secrets.sh` | **PASS** |
| `bash scripts/security/ci-backend-rc-readiness.sh` | **PASS_WITH_ENVIRONMENT_SKIPS** |
| `bash scripts/security/ci-backend-docker-check.sh` | **PASS** |

## 9. Sonraki adım

1. Değişiklikleri commit/PR; GitHub **Backend Docker CI** workflow `main` üzerinde doğrula.
2. Staging PP-1..PP-3 + pre-prod bundle `GO` (Faz 125).
3. Flag flip CR (Faz 126) — production enable yalnızca onaylı GitOps ile.
