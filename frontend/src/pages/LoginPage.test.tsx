import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { LoginPage } from './LoginPage'

const listSsoProvidersMock = vi.hoisted(() =>
  vi.fn(async () => ({
    providers: [{ registrationId: 'generic-oidc', label: 'OIDC' }],
  })),
)

vi.mock('../features/auth/auth-api', () => ({
  login: vi.fn(async () => ({})),
  loginSchema: { safeParse: () => ({ success: false }) },
  me: vi.fn(async () => ({ userId: 'u1', email: 'a@example.com', name: 'A', roles: [] })),
  authUserFromMeResponse: (m: { userId: string; email: string; name: string }) => ({
    id: m.userId,
    email: m.email,
    name: m.name,
  }),
  listSsoProviders: listSsoProvidersMock,
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
  isSsoEnabled: () => true,
}))

describe('LoginPage', () => {
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
    expect(await screen.findByText('Continue with OIDC')).toBeTruthy()
  })

  it('shows safe SSO error message from redirect', async () => {
    renderPage('/login?error=SSO_STATE_INVALID')
    expect(await screen.findByText(/sign-in could not be completed/i)).toBeTruthy()
    expect(screen.queryByText(/SSO_STATE_INVALID/)).toBeNull()
  })
})
