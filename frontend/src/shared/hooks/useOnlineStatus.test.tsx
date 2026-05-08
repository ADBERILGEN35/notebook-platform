import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useOnlineStatus } from './useOnlineStatus'

function Probe() {
  const { isOnline } = useOnlineStatus()
  return <span data-testid="status">{isOnline ? 'online' : 'offline'}</span>
}

describe('useOnlineStatus', () => {
  it('reacts to browser online/offline events', () => {
    render(<Probe />)
    expect(screen.getByTestId('status').textContent).toBe('online')
    fireEvent(window, new Event('offline'))
    expect(screen.getByTestId('status').textContent).toBe('offline')
    fireEvent(window, new Event('online'))
    expect(screen.getByTestId('status').textContent).toBe('online')
  })
})
