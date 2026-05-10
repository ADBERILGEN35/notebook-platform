import { z } from 'zod'
import { apiRequest } from '../../shared/api/api-client'
import type { AuthResponse, AuthUser } from '../../shared/types/api'
import { isCookieMode } from '../../shared/config/auth-transport'

export const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
})

export const signupSchema = z.object({
  email: z.string().email(),
  password: z.string().min(10),
  name: z.string().min(2),
  avatarUrl: z.string().url().optional().or(z.literal('')),
})

export type LoginInput = z.infer<typeof loginSchema>
export type SignupInput = z.infer<typeof signupSchema>
export type SsoProvider = {
  registrationId: string
  label: string
}

export const login = (payload: LoginInput) =>
  apiRequest<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })

export const signup = (payload: SignupInput) =>
  apiRequest<AuthResponse>('/auth/signup', {
    method: 'POST',
    body: JSON.stringify({ ...payload, avatarUrl: payload.avatarUrl || null }),
  })

export type MeResponse = {
  userId: string
  email: string
  roles: string[]
  name: string
  avatarUrl?: string | null
  platformRoles?: string[]
  platformPermissions?: string[]
}

export const me = () => apiRequest<MeResponse>('/auth/me')

export function authUserFromMeResponse(m: MeResponse, status?: string): AuthUser {
  return {
    id: m.userId,
    email: m.email,
    name: m.name,
    avatarUrl: m.avatarUrl ?? null,
    status,
    roles: m.roles ?? [],
    platformRoles: m.platformRoles,
    platformPermissions: m.platformPermissions,
  }
}

export const listSsoProviders = () =>
  apiRequest<{ providers: SsoProvider[] }>('/auth/sso/providers')

export const logout = (refreshToken?: string | null) =>
  apiRequest<void>('/auth/logout', {
    method: 'POST',
    body: JSON.stringify(isCookieMode() ? {} : { refreshToken }),
  })

export const revokeAll = () =>
  apiRequest<{ revokedCount: number }>('/auth/revoke-all', {
    method: 'POST',
    body: JSON.stringify({}),
  })

