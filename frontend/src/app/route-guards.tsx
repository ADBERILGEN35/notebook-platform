import { Navigate, Outlet } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuthStore } from '../features/auth/auth-store'
import { canShowAdminNavigation } from '../features/admin/access/admin-access'
import { isAdminUiEnabled } from '../shared/config/admin-feature-flags'
import { isCookieMode } from '../shared/config/auth-transport'
import { PermissionDenied } from '../shared/components/PermissionDenied'

export function Protected({ children }: { children: ReactNode }) {
  const token = useAuthStore((state) => state.accessToken)
  const user = useAuthStore((state) => state.user)
  if (isCookieMode()) {
    if (!user) return <Navigate to="/login" replace />
    return <>{children}</>
  }
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

export function AdminGate() {
  const user = useAuthStore((state) => state.user)
  if (!isAdminUiEnabled()) {
    return <Navigate to="/app" replace />
  }
  if (!canShowAdminNavigation(user)) {
    return (
      <PermissionDenied
        title="Admin area restricted"
        message="Admin UI requires feature flags and a trusted role. Local development may set ADMIN_UI_DEV_OPEN; production requires platform admin authorization (planned Faz 43)."
      />
    )
  }
  return <Outlet />
}
