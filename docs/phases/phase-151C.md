# Faz 151C: AppShell + Workspace Dashboard Layout Visual Alignment

**Durum:** Uygulama
**Önceki:** [phase-151B-summary.md](phase-151B-summary.md)
**Backend:** Değişiklik yok | **Production flag:** Açılmaz

## Sorun

Faz 151B outlet içeriğini (hero / quick actions / recently viewed) tasarıma yaklaştırdı, ancak `AppShell` / `SideNav` / `TopNav` / global background bilinçli olarak dokunulmamıştı. Sonuç: local `/app` ekranı hâlâ "default" duruyor:

- body `#f5f7fb` mavi-gri ↔ tasarım `surface` (`#fcf8ff`) lavender
- sidebar `w-72`, brand-line tek text-link, ana navigation Workspaces/Notebooks listesiyle dominant
- topbar workspace-switcher + dikdörtgen input + "Create note" + bell + UserMenu — tasarımdaki rounded-full search pill + Drafts/Shared/Archived + Invite Team CTA + avatar yapısından farklı
- main wrapper `max-w-6xl` ve dar padding nedeniyle hero/quick actions/recently viewed sıkışıyor

## Kök neden

`SideNav` ve `TopNav`, workspace-switching ve note-create gibi **çalışma-app ihtiyaçlarına** göre tasarlanmıştı. Tasarım, **enterprise dashboard** odaklı bir kabuk öneriyor (sade nav rail + sticky topbar + ferah canvas). 151B bilinçli olarak bu bileşenlere dokunmadığı için görsel mismatch sürdü.

## Çözüm

1. **Global background**: `body` → `#fcf8ff` (tasarım `surface`).
2. **SideNav v2**: `w-60` (240px), NP rozeti + "Enterprise Workspace" subtitle, primary "New Notebook" CTA (`onCreateNotebook`), sade nav linkleri (Workspaces / Search / Notifications / Settings / Admin), alt sticky "Support" + "Sign Out" (logout mutasyonu UserMenu'den taşındı). Workspace switcher artık tasarım uyarınca workspaces listesi `<details>` içine alındı (default açık; testler bozulmaz).
3. **TopNav v2**: solda rounded-full search pill, sağda secondary nav (Drafts / Shared / Archived — mevcut filtered route yoksa `aria-disabled` ile safe shell), utility (NotificationBell), "Invite Team" CTA (active workspace varsa `/members`, yoksa disabled), `UserMenu` avatar. Header sticky h-16. `onCreateNote` Create CTA'sı sidebar "New Notebook"a taşındı.
4. **Canvas genişliği**: `ResponsiveContent` yeni `2xl` (max-w-[1280px]) seçeneği; `WorkspaceHubPage` `2xl` kullanır.
5. **API davranışı**: `listWorkspaces`/`listNotebooks` yüzeyleri aynı; sadece UI yeniden düzenlendi.

## Test gereksinimleri

- AppShell desktop render
- SideNav: brand + new-notebook CTA + nav linkleri
- TopNav: search pill + secondary nav + invite team + avatar
- Mobile drawer regression (SideNav drawer içinde de çalışır)
- WorkspaceHub empty/populated bozulmamış
- Sign-out logout flow
- Token/secret yok

## Sign-off

Frontend **GO_WITH_ACCEPTED_RISKS**; backend NO_GO platform-genelinde değişmedi.
