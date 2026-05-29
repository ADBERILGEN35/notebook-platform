import type { PropsWithChildren, ReactNode } from 'react'
import { SkipToMain } from '../../../shared/components/SkipToMain'
import { SideNav } from './SideNav'
import { TopNav } from './TopNav'
import { MobileNav } from './MobileNav'
import { MobileNavDrawer } from './MobileNavDrawer'
import type { AppShellNavProps, AppShellTopProps } from '../types'

type AppShellProps = PropsWithChildren<{
  nav: AppShellNavProps
  top: AppShellTopProps
  isDesktop: boolean
  mobileNavOpen: boolean
  onMobileNavOpen: () => void
  onMobileNavClose: () => void
  banners?: ReactNode
}>

export function AppShell({
  children,
  nav,
  top,
  isDesktop,
  mobileNavOpen,
  onMobileNavOpen,
  onMobileNavClose,
  banners,
}: AppShellProps) {
  return (
    <div className="flex min-h-screen overflow-x-hidden bg-surface">
      <SkipToMain />
      {isDesktop ? <SideNav {...nav} /> : null}
      <MobileNavDrawer open={!isDesktop && mobileNavOpen} onClose={onMobileNavClose} {...nav} />
      <div className="flex min-h-screen flex-1 flex-col pb-16 lg:pb-0">
        <TopNav {...top} onSidebarToggle={onMobileNavOpen} />
        <main
          className="flex-1 px-4 py-6 sm:px-6 lg:px-10 lg:py-8"
          id="main-content"
          tabIndex={-1}
        >
          {banners}
          {children}
        </main>
        {!isDesktop ? <MobileNav onOpenMenu={onMobileNavOpen} /> : null}
      </div>
    </div>
  )
}
