import { RouterProvider } from 'react-router-dom'
import { router } from './router'
import { ErrorBoundary } from './ErrorBoundary'
import { useEffect } from 'react'
import { authUserFromMeResponse, me } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import { isCookieMode } from '../shared/config/auth-transport'

export default function App() {
  const setUser = useAuthStore((state) => state.setUser)
  const clearSession = useAuthStore((state) => state.clearSession)

  useEffect(() => {
    if (isCookieMode()) {
      void me()
        .then((response) => {
          setUser(authUserFromMeResponse(response, 'ACTIVE'))
        })
        .catch(() => {
          clearSession()
        })
      return
    }
    const token = useAuthStore.getState().accessToken
    if (!token) return
    void me()
      .then((response) => {
        setUser(
          authUserFromMeResponse(response, useAuthStore.getState().user?.status ?? 'ACTIVE'),
        )
      })
      .catch(() => {
        clearSession()
      })
  }, [clearSession, setUser])

  return (
    <ErrorBoundary>
      <RouterProvider router={router} />
    </ErrorBoundary>
  )
}

