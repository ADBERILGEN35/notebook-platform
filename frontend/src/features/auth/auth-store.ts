import { create } from 'zustand'
import type { AuthUser } from '../../shared/types/api'

const ACCESS_TOKEN_KEY = 'np_access_token'
const REFRESH_TOKEN_KEY = 'np_refresh_token'
const USER_KEY = 'np_user'

type AuthState = {
  accessToken: string | null
  refreshToken: string | null
  user: AuthUser | null
  setSession: (payload: { accessToken: string; refreshToken: string; user: AuthUser }) => void
  clearSession: () => void
}

const readUser = (): AuthUser | null => {
  const value = localStorage.getItem(USER_KEY)
  if (!value) return null
  try {
    return JSON.parse(value) as AuthUser
  } catch {
    return null
  }
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: localStorage.getItem(ACCESS_TOKEN_KEY),
  refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY),
  user: readUser(),
  setSession: ({ accessToken, refreshToken, user }) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    set({ accessToken, refreshToken, user })
  },
  clearSession: () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    set({ accessToken: null, refreshToken: null, user: null })
  },
}))

