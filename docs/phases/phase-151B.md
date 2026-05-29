# Faz 151B: Workspace Dashboard Visual Mismatch Fix

**Durum:** Uygulama
**Önceki:** [phase-151A-summary.md](phase-151A-summary.md) (151A bileşen iskeleti)
**Backend:** Değişiklik yok | **Production flag:** Açılmaz

## Sorun

Localde `/app` ekranı, `docs/design/workspace_dashboard_empty` ve `_populated` tasarımıyla görsel olarak uyuşmuyor. Faz 151A bileşenleri eklendi fakat empty path'te **OnboardingWizard fullscreen olarak dashboard'u eziyor**; tasarımdaki hero/quick actions/recently viewed skeleton hiç görünmüyor.

## Kök neden

`WorkspaceHubPage` içindeki şu koşul yeni kullanıcıda yalnızca `OnboardingWizard` render ediyor, yeni `WorkspaceDashboardEmpty` bileşenini bypass ediyor:

```ts
if (!workspaces.length && showOnboarding) {
  return <ResponsiveContent><PageHeader /><OnboardingWizard /></ResponsiveContent>
}
```

Ek olarak:
- `WorkspaceGettingStartedCard` CTA'sı tasarımdaki "Create New Notebook / Browse Templates" ile uyuşmuyor
- `WorkspaceQuickActions` tasarımdaki "Quick Note / Invite Member" ikonlu kompakt kartlardan farklı liste şeklinde
- `RecentlyViewedPlaceholder` skeleton kartı tasarımdaki opacity-tiered kart düzeninden farklı şekilde — düzelt
- Onboarding kullanıcı tercihini bozmadan dashboard içinde **opsiyonel "Get started" paneli** olarak gömülmeli

## Çözüm

1. **OnboardingWizard fullscreen branch'i kaldır.** `WorkspaceHubPage` her zaman empty/populated dashboard render eder.
2. **`GettingStartedPanel`** — onboarding tamamlanmamışsa empty dashboard üstüne kompakt "Get started" paneli olarak gömülür (collapsible/dismissable, `localStorage` kararını bozmaz).
3. **`WorkspaceDashboardHero`** — tasarımdaki büyük hero card (purple decorative blur + display heading + 3 adımlı listede özet + primary/secondary CTA).
4. **`WorkspaceQuickActions`** — kompakt iki kart (Quick Note + Invite Member) tasarımdaki sidebar widget'a yakın.
5. **`RecentlyViewedSection`** — production path'te workspace yokken **skeleton** (mock veri yok); workspace varsa gerçek notebook listesi.
6. Bento grid: empty path'te 8/4 + alt 12 kolonlu Recently Viewed.

## API davranışı (korunan)

- `listWorkspaces` boş → empty dashboard (default)
- workspace var → populated dashboard
- loading/error korunur

## Test gereksinimleri

- `/app` empty render + hero + quick actions + recently viewed skeleton
- `/app` populated render
- GettingStartedPanel onboarding entegrasyonu
- 150 testleri PASS, 151A testleri PASS
- Token/secret yok

## Sign-off

Frontend **GO_WITH_ACCEPTED_RISKS** kalır; platform **NO_GO** (backend PP).
