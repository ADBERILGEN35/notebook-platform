import { useMutation } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { logout } from '../../auth/auth-api'
import { useAuthStore } from '../../auth/auth-store'
import { isCookieMode } from '../../../shared/config/auth-transport'

export function UserMenu() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const refreshToken = useAuthStore((state) => state.refreshToken)
  const clearSession = useAuthStore((state) => state.clearSession)

  const logoutMutation = useMutation({
    mutationFn: () => logout(isCookieMode() ? null : refreshToken),
    onSuccess: () => {
      clearSession()
      navigate('/login', { replace: true })
    },
  })

  const initial = (user?.name?.trim()?.[0] || user?.email?.trim()?.[0] || '?').toUpperCase()

  return (
    <details className="relative">
      <summary
        className="flex cursor-pointer list-none items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-2 py-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary [&::-webkit-details-marker]:hidden"
        aria-label="User menu"
      >
        <span
          className="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-semibold text-white"
          aria-hidden
        >
          {initial}
        </span>
        <span className="hidden max-w-[8rem] truncate text-body-md text-on-surface sm:inline">{user?.name || 'Account'}</span>
      </summary>
      <ul className="absolute right-0 z-20 mt-2 min-w-[10rem] list-none rounded-lg border border-outline-variant bg-surface-container-lowest py-1 shadow-auth-card">
        <li>
          <Link
            to="/app/settings"
            className="block px-4 py-2 text-body-md text-on-surface hover:bg-surface-container-low focus-visible:bg-surface-container-low"
          >
            Settings
          </Link>
        </li>
        <li>
          <button
            type="button"
            className="w-full px-4 py-2 text-left text-body-md text-on-surface hover:bg-surface-container-low"
            onClick={() => logoutMutation.mutate()}
          >
            Sign out
          </button>
        </li>
      </ul>
    </details>
  )
}
