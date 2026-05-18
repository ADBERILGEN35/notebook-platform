import type { ReactElement } from 'react'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'
import { RegisterPage } from './RegisterPage'
import { ForgotPasswordPage } from './ForgotPasswordPage'
import { MfaAuthenticationPage } from './MfaAuthenticationPage'
import { SsoCallbackPage } from './SsoCallbackPage'

vi.mock('../features/auth/auth-api', () => ({
  login: vi.fn(),
  signup: vi.fn(),
  me: vi.fn(async () => ({ userId: 'u1', email: 'a@example.com', name: 'A', roles: [] })),
  loginSchema: { safeParse: () => ({ success: false }) },
  signupSchema: { safeParse: () => ({ success: false }) },
  authUserFromMeResponse: (m: { userId: string; email: string; name: string }) => ({
    id: m.userId,
    email: m.email,
    name: m.name,
  }),
  listSsoProviders: vi.fn(async () => ({ providers: [] })),
}))

vi.mock('../features/auth/mfa-api', () => ({
  authenticationOptions: vi.fn(),
  authenticationVerify: vi.fn(),
  verifyRecoveryCode: vi.fn(),
}))

vi.mock('../features/auth/auth-store', () => ({
  useAuthStore: (selector: (state: { setSession: () => void; setUser: () => void }) => unknown) =>
    selector({ setSession: vi.fn(), setUser: vi.fn() }),
}))

vi.mock('../shared/config/sso-feature-flags', () => ({
  isSsoEnabled: () => false,
}))

vi.mock('../features/auth/mfa-session', () => ({
  getMfaSessionId: () => null,
  clearMfaSessionId: vi.fn(),
  setMfaSessionId: vi.fn(),
}))

const wrap = (ui: ReactElement, path = '/') =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <QueryClientProvider client={new QueryClient()}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  )

describe('Auth pages', () => {
  it('LoginPage renders', () => {
    wrap(<LoginPage />, '/login')
    expect(screen.getByRole('heading', { name: /notebook platform/i })).toBeTruthy()
    expect(screen.getByLabelText('Email')).toBeTruthy()
    expect(screen.getByLabelText('Password')).toBeTruthy()
  })

  it('RegisterPage renders', () => {
    wrap(<RegisterPage />)
    expect(screen.getByRole('heading', { name: /create your workspace/i })).toBeTruthy()
    expect(screen.getByLabelText('Full name')).toBeTruthy()
  })

  it('ForgotPasswordPage renders', () => {
    wrap(<ForgotPasswordPage />)
    expect(screen.getByRole('heading', { name: /reset your password/i })).toBeTruthy()
  })

  it('MfaAuthenticationPage redirects without session', () => {
    wrap(<MfaAuthenticationPage />, '/mfa')
    expect(screen.queryByText(/verify your identity/i)).toBeNull()
  })

  it('SsoCallbackPage shows loading state', () => {
    wrap(<SsoCallbackPage />, '/sso/callback')
    expect(screen.getByRole('status', { name: /loading/i })).toBeTruthy()
    expect(screen.getByText(/verifying your identity/i)).toBeTruthy()
  })

  it('does not render raw tokens in login markup', () => {
    const { container } = render(
      <MemoryRouter>
        <QueryClientProvider client={new QueryClient()}>
          <LoginPage />
        </QueryClientProvider>
      </MemoryRouter>,
    )
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
    expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]+/)
  })
})
