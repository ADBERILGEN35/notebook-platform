import { create } from 'zustand'
import type { AuthUser } from '../../shared/types/api'
import { isCookieMode } from '../../shared/config/auth-transport'
import { clearOfflineNotes } from '../offline/offline-note-cache'
import { clearOfflineEncryptionKey, ensureOfflineEncryptionKey } from '../offline/offline-crypto'
import { isOfflineEncryptionEnabled } from '../../shared/config/offline-feature-flags'

const ACCESS_TOKEN_KEY = 'np_access_token'
const REFRESH_TOKEN_KEY = 'np_refresh_token'
const USER_KEY = 'np_user'

type AuthState = {
  accessToken: string | null
  refreshToken: string | null
  user: AuthUser | null
  setSession: (payload: { accessToken?: string | null; refreshToken?: string | null; user: AuthUser }) => void
  setUser: (user: AuthUser | null) => void
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
  accessToken: isCookieMode() ? null : localStorage.getItem(ACCESS_TOKEN_KEY),
  refreshToken: isCookieMode() ? null : localStorage.getItem(REFRESH_TOKEN_KEY),
  user: readUser(),
  setSession: ({ accessToken, refreshToken, user }) => {
    const cookieMode = isCookieMode()
    if (!cookieMode && accessToken) {
      localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    } else {
      localStorage.removeItem(ACCESS_TOKEN_KEY)
    }
    if (!cookieMode && refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY)
    }
    localStorage.setItem(USER_KEY, JSON.stringify(user))
    set({
      accessToken: cookieMode ? null : accessToken ?? null,
      refreshToken: cookieMode ? null : refreshToken ?? null,
      user,
    })
    if (isOfflineEncryptionEnabled()) {
      void ensureOfflineEncryptionKey()
    }
  },
  setUser: (user) => {
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify(user))
    } else {
      localStorage.removeItem(USER_KEY)
    }
    set({ user })
  },
  clearSession: () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    clearOfflineEncryptionKey()
    void clearOfflineNotes()
    set({ accessToken: null, refreshToken: null, user: null })
  },
}))

if (isOfflineEncryptionEnabled() && readUser()) {
  void ensureOfflineEncryptionKey()
}

