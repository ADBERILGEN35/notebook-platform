import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { AdminNotificationDeadLetterPage } from './AdminNotificationDeadLetterPage'
import * as deadLetterApi from '../../features/admin/notification-dead-letter-api'

const { mockUseAuthStore } = vi.hoisted(() => ({
  mockUseAuthStore: vi.fn(),
}))

vi.mock('../../shared/config/admin-feature-flags', () => ({
  isNotificationDeadLetterUiEnabled: () => true,
}))

vi.mock('../../features/auth/auth-store', () => ({
  useAuthStore: (selector: (s: { user: unknown }) => unknown) => mockUseAuthStore(selector),
}))

describe('AdminNotificationDeadLetterPage', () => {
  beforeEach(() => {
    mockUseAuthStore.mockReset()
    vi.restoreAllMocks()
  })

  it('shows permission message without read permission', () => {
    mockUseAuthStore.mockImplementation((selector) =>
      selector({
        user: { id: 'u1', roles: ['USER'], platformPermissions: [] },
      }),
    )
    render(
      <MemoryRouter>
        <AdminNotificationDeadLetterPage />
      </MemoryRouter>,
    )
    expect(screen.getByText(/do not have permission to view dead-letter/i)).toBeInTheDocument()
  })

  it('loads table when platform admin', async () => {
    mockUseAuthStore.mockImplementation((selector) =>
      selector({
        user: { id: 'a1', roles: ['PLATFORM_ADMIN'], platformPermissions: [] },
      }),
    )
    vi.spyOn(deadLetterApi, 'fetchDeadLetterList').mockResolvedValue({
      items: [
        {
          id: 'row-1',
          source: 'FANOUT_OUTBOX',
          eventType: 'notification.created',
          recipientUserIdHash: 'abc',
          status: 'DEAD',
          attemptCount: 3,
          requeueCount: 0,
          lastErrorCode: 'X',
          lastErrorSummary: 'y',
          createdAt: '2026-05-10T00:00:00Z',
          updatedAt: '2026-05-10T00:00:00Z',
          deadAt: '2026-05-10T01:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    })
    render(
      <MemoryRouter>
        <AdminNotificationDeadLetterPage />
      </MemoryRouter>,
    )
    await waitFor(() => expect(deadLetterApi.fetchDeadLetterList).toHaveBeenCalled())
    expect(screen.getByText('notification.created')).toBeInTheDocument()
  })
})
