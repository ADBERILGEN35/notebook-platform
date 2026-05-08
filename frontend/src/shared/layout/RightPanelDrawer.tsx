import type { ReactNode } from 'react'
import { ResponsiveDrawer } from '../components/ResponsiveDrawer'

export function RightPanelDrawer({
  open,
  onClose,
  children,
}: {
  open: boolean
  onClose: () => void
  children: ReactNode
}) {
  return (
    <ResponsiveDrawer open={open} onClose={onClose} title="Details" side="right" testId="mobile-right-panel">
      {children}
    </ResponsiveDrawer>
  )
}
