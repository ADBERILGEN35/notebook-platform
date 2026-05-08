import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { LoginPage } from './LoginPage'

const listSsoProvidersMock = vi.hoisted(() =>
  vi.fn(async () => ({
    providers: [{ registrationId: 'generic-oidc', label: 'Continue with generic-oidc' }],
  })),
)

vi.mock('../features/auth/auth-api', () => ({
  login: vi.fn(async () => ({})),
  loginSchema: { safeParse: () => ({ success: false }) },
  me: vi.fn(async () => ({ userId: 'u1', email: 'a@example.com', name: 'A', roles: [] })),
  listSsoProviders: listSsoProvidersMock,
}))

vi.mock('../features/auth/mfa-api', () => ({
  authenticationOptions: vi.fn(),
  authenticationVerify: vi.fn(),
  verifyRecoveryCode: vi.fn(),
}))

vi.mock('../features/auth/auth-store', () => ({
  useAuthStore: (selector: (state: any) => any) =>
    selector({ setSession: vi.fn(), setUser: vi.fn() }),
}))

vi.mock('../shared/config/sso-feature-flags', () => ({
  isSsoEnabled: () => true,
}))

describe('LoginPage SSO', () => {
  beforeEach(() => {
    listSsoProvidersMock.mockClear()
  })

  const renderPage = (initialPath = '/login') =>
    render(
      <MemoryRouter initialEntries={[initialPath]}>
        <QueryClientProvider client={new QueryClient()}>
          <LoginPage />
        </QueryClientProvider>
      </MemoryRouter>,
    )

  it('renders SSO providers', async () => {
    renderPage()
    expect(await screen.findByText('Continue with generic-oidc')).toBeTruthy()
  })

  it('shows query error code from SSO redirect', async () => {
    renderPage('/login?error=SSO_STATE_INVALID')
    expect(await screen.findByText('SSO login failed: SSO_STATE_INVALID')).toBeTruthy()
  })

})
