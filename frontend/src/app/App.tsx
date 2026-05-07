import { RouterProvider } from 'react-router-dom'
import { router } from './router'
import { useEffect } from 'react'
import { me } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import { isCookieMode } from '../shared/config/auth-transport'

export default function App() {
  const setUser = useAuthStore((state) => state.setUser)
  const clearSession = useAuthStore((state) => state.clearSession)

  useEffect(() => {
    if (isCookieMode()) {
      void me()
        .then((response) => {
          setUser({
            id: response.userId,
            email: response.email,
            name: response.name,
            avatarUrl: response.avatarUrl ?? null,
            status: 'ACTIVE',
            roles: response.roles ?? [],
          })
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
        setUser({
          id: response.userId,
          email: response.email,
          name: response.name,
          avatarUrl: response.avatarUrl ?? null,
          status: useAuthStore.getState().user?.status ?? 'ACTIVE',
          roles: response.roles ?? [],
        })
      })
      .catch(() => {
        clearSession()
      })
  }, [clearSession, setUser])

  return <RouterProvider router={router} />
}

