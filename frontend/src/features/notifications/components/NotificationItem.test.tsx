import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { NotificationItem } from './NotificationItem'

const baseNotification = {
  id: 'n1',
  title: 'Security notice',
  message: 'Sessions revoked.',
  severity: 'WARNING' as const,
  unread: true,
  createdAt: new Date().toISOString(),
  type: 'SECURITY_SESSIONS_REVOKED' as const,
}

describe('NotificationItem', () => {
  it('renders internal action link only', () => {
    render(
      <MemoryRouter>
        <NotificationItem
          notification={{ ...baseNotification, actionUrl: '/app/settings/security' }}
          onMarkRead={vi.fn()}
          onArchive={vi.fn()}
        />
      </MemoryRouter>,
    )
    expect(screen.getByText('Open')).toBeTruthy()
  })

  it('does not render external action link', () => {
    render(
      <MemoryRouter>
        <NotificationItem
          notification={{ ...baseNotification, actionUrl: 'https://malicious.example' }}
          onMarkRead={vi.fn()}
          onArchive={vi.fn()}
        />
      </MemoryRouter>,
    )
    expect(screen.queryByText('Open')).toBeNull()
  })
})
