import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { NotificationDropdown } from './NotificationDropdown'

vi.mock('../../../shared/hooks/useMediaQuery', () => ({
  useMediaQuery: () => true,
}))

vi.mock('../notification-hooks', () => ({
  useNotifications: () => ({ data: { items: [] }, isLoading: false, isError: false }),
  useMarkNotificationRead: () => ({ mutate: vi.fn() }),
  useArchiveNotification: () => ({ mutate: vi.fn() }),
}))

describe('NotificationDropdown mobile', () => {
  it('renders as bottom sheet in mobile mode', () => {
    render(
      <MemoryRouter>
        <NotificationDropdown open />
      </MemoryRouter>,
    )
    expect(screen.getByText('Notifications')).toBeTruthy()
    expect(screen.getByText('View all')).toBeTruthy()
  })
})
