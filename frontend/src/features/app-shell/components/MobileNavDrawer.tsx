import type { AppShellNavProps } from '../types'
import { ResponsiveDrawer } from '../../../shared/components/ResponsiveDrawer'
import { SideNav } from './SideNav'

type MobileNavDrawerProps = AppShellNavProps & {
  open: boolean
  onClose: () => void
}

export function MobileNavDrawer({ open, onClose, ...navProps }: MobileNavDrawerProps) {
  return (
    <ResponsiveDrawer open={open} onClose={onClose} title="Navigation" side="left" testId="mobile-sidebar">
      <SideNav {...navProps} onNavigate={onClose} className="w-full border-r-0 p-0" />
    </ResponsiveDrawer>
  )
}
