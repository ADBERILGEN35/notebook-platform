import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ResponsiveDrawer } from './ResponsiveDrawer'

describe('ResponsiveDrawer', () => {
  it('closes on escape', () => {
    const onClose = vi.fn()
    render(
      <ResponsiveDrawer open onClose={onClose} title="Test drawer">
        <div>content</div>
      </ResponsiveDrawer>,
    )
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(onClose).toHaveBeenCalled()
  })

  it('closes on backdrop click', () => {
    const onClose = vi.fn()
    const { container } = render(
      <ResponsiveDrawer open onClose={onClose} title="Test drawer">
        <div>content</div>
      </ResponsiveDrawer>,
    )
    const backdrop = container.querySelector('.fixed.inset-0')
    if (backdrop) fireEvent.click(backdrop)
    expect(onClose).toHaveBeenCalled()
    expect(screen.getByText('content')).toBeTruthy()
  })
})
