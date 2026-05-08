import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { Topbar } from './Topbar'

vi.mock('../hooks/useMediaQuery', () => ({
  useMediaQuery: () => true,
}))

vi.mock('../../features/notifications/components/NotificationBell', () => ({
  NotificationBell: () => <span>Bell</span>,
}))

describe('Topbar responsive', () => {
  it('renders sidebar toggle and triggers callback', () => {
    const onSidebarToggle = vi.fn()
    render(
      <MemoryRouter>
        <Topbar search="" onSearchChange={vi.fn()} onCreateNote={vi.fn()} onSidebarToggle={onSidebarToggle} />
      </MemoryRouter>,
    )
    fireEvent.click(screen.getByTestId('sidebar-toggle'))
    expect(onSidebarToggle).toHaveBeenCalled()
  })
})
