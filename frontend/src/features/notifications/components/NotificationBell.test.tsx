import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { NotificationBell } from './NotificationBell'

const enabledState = vi.hoisted(() => ({ enabled: true, unread: 3 }))

vi.mock('../../../shared/config/notifications-feature-flags', () => ({
  isNotificationsEnabled: () => enabledState.enabled,
}))

vi.mock('../notification-hooks', () => ({
  useUnreadNotificationCount: () => ({ data: { unreadCount: enabledState.unread } }),
  useNotifications: () => ({ data: { items: [] } }),
  useMarkNotificationRead: () => ({ mutate: vi.fn() }),
  useArchiveNotification: () => ({ mutate: vi.fn() }),
}))

describe('NotificationBell', () => {
  it('hides when feature is disabled', () => {
    enabledState.enabled = false
    render(<NotificationBell />)
    expect(screen.queryByLabelText('Notifications')).toBeNull()
  })

  it('shows unread count badge when enabled', () => {
    enabledState.enabled = true
    enabledState.unread = 5
    render(<NotificationBell />)
    expect(screen.getByLabelText('Notifications')).toBeTruthy()
    expect(screen.getByText('5')).toBeTruthy()
  })
})
