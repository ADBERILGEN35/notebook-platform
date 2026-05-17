import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BreakGlassActiveSessionsPanel } from './BreakGlassActiveSessionsPanel'

vi.mock('../../auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: { platformPermissions: string[] } }) => unknown) =>
    selector({ user: { platformPermissions: ['admin:break-glass:read'] } }),
}))

vi.mock('../../../shared/config/admin-feature-flags', () => ({
  isBreakGlassRevocationUiEnabled: () => true,
}))

vi.mock('./admin-break-glass-sessions-api', () => ({
  listBreakGlassActiveSessions: vi.fn(async () => ({
    items: [
      {
        eventId: 'e1',
        sessionId: 'sess-short',
        revokeRef: 'sess-full-id',
        jtiMasked: 'abcd…wxyz',
        mode: 'static-token',
        actorLabel: 'actor',
        status: 'ISSUED',
        tokenStatus: 'ACTIVE',
        issuedAt: '2026-01-01T00:00:00Z',
        expiresAt: '2026-01-01T00:15:00Z',
        revocationSource: '',
      },
    ],
    activeCount: 1,
  })),
  revokeBreakGlassSession: vi.fn(),
  revokeAllBreakGlassActiveSessions: vi.fn(),
}))

describe('BreakGlassActiveSessionsPanel', () => {
  it('renders masked jti without raw token', async () => {
    const qc = new QueryClient()
    render(
      <QueryClientProvider client={qc}>
        <BreakGlassActiveSessionsPanel />
      </QueryClientProvider>,
    )
    expect(await screen.findByText('abcd…wxyz')).toBeInTheDocument()
    expect(screen.queryByText(/eyJ/)).toBeNull()
    expect(screen.queryByText('sess-full-id')).toBeNull()
  })
})
