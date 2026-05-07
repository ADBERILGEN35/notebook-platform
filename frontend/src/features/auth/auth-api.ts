import { z } from 'zod'
import { apiRequest } from '../../shared/api/api-client'
import type { AuthResponse } from '../../shared/types/api'

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

export const logout = (refreshToken: string) =>
  apiRequest<void>('/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  })

export const revokeAll = () =>
  apiRequest<{ revokedCount: number }>('/auth/revoke-all', {
    method: 'POST',
    body: JSON.stringify({}),
  })

