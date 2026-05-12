import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, afterEach } from 'vitest'
import { AdminBreakGlassRotationPage } from './AdminBreakGlassRotationPage'
import * as rotationApi from '../../features/admin/breakglass/admin-break-glass-rotation-api'
import {
  PERM_BREAK_GLASS_ROTATION_MANAGE,
  PERM_BREAK_GLASS_ROTATION_READ,
} from '../../features/admin/access/admin-permissions'

vi.mock('../../shared/config/admin-feature-flags', () => ({
  isBreakGlassRotationUiEnabled: () => true,
}))

const mockUser = {
  id: 'u1',
  email: 'admin@b.com',
  name: 'A',
  roles: [],
  platformPermissions: [PERM_BREAK_GLASS_ROTATION_READ, PERM_BREAK_GLASS_ROTATION_MANAGE],
}

vi.mock('../../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: Record<string, unknown> | null }) => unknown) =>
    selector({ user: mockUser }),
}))

describe('AdminBreakGlassRotationPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  const renderPage = () => {
    const qc = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    return render(
      <QueryClientProvider client={qc}>
        <MemoryRouter>
          <AdminBreakGlassRotationPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )
  }

  it('renders status cards and rotation events without exposing token material', async () => {
    vi.spyOn(rotationApi, 'listBreakGlassRotationEvents').mockResolvedValue({
      items: [
        {
          id: 'r1',
          credentialMode: 'static-token',
          status: 'REQUIRED',
          oldFingerprint: 'fp:abcd1234',
          newFingerprint: null,
          triggeredByEventId: 'e1',
          triggeredBySessionId: 'sess-abc',
          requiredAt: '2026-05-11T10:00:00Z',
          acknowledgedAt: null,
          verifiedAt: null,
          closedAt: null,
        },
      ],
      total: 1,
      openCount: 1,
      requiredCount: 1,
    })

    renderPage()
    await waitFor(() => {
      expect(screen.getByText('REQUIRED')).toBeInTheDocument()
    })
    expect(screen.getByText('fp:abcd1234')).toBeInTheDocument()
    expect(
      screen.getByText(/The token value is never shown/i),
    ).toBeInTheDocument()
    // No raw secrets / no full hash
    expect(document.body.textContent).not.toContain('sha256:')
  })

  it('disables submit until a 10+ char reason is entered', async () => {
    vi.spyOn(rotationApi, 'listBreakGlassRotationEvents').mockResolvedValue({
      items: [
        {
          id: 'r2',
          credentialMode: 'static-token',
          status: 'REQUIRED',
          oldFingerprint: 'fp:zzzz0000',
          newFingerprint: null,
          triggeredByEventId: null,
          triggeredBySessionId: null,
          requiredAt: '2026-05-11T11:00:00Z',
          acknowledgedAt: null,
          verifiedAt: null,
          closedAt: null,
        },
      ],
      total: 1,
      openCount: 1,
      requiredCount: 1,
    })
    vi.spyOn(rotationApi, 'getBreakGlassRotationEvent').mockResolvedValue({
      id: 'r2',
      credentialMode: 'static-token',
      status: 'REQUIRED',
      oldFingerprint: 'fp:zzzz0000',
      newFingerprint: null,
      triggeredByEventId: null,
      triggeredBySessionId: null,
      requiredAt: '2026-05-11T11:00:00Z',
      acknowledgedAt: null,
      verifiedAt: null,
      closedAt: null,
    })

    renderPage()
    const user = userEvent.setup()

    await waitFor(() => expect(screen.getByText('REQUIRED')).toBeInTheDocument())
    await user.click(screen.getByText('REQUIRED'))
    await waitFor(() =>
      expect(screen.getByText(/Rotation event r2/i)).toBeInTheDocument(),
    )

    const submit = screen.getByRole('button', { name: 'Submit' })
    expect(submit).toBeDisabled()

    const reasonInput = screen.getByPlaceholderText(/Reason \(min 10 chars\)/i)
    await user.type(reasonInput, 'short')
    expect(submit).toBeDisabled()

    await user.clear(reasonInput)
    await user.type(reasonInput, 'Begin External Secret rotation procedure.')
    expect(submit).toBeEnabled()
  })
})
