import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useMediaQuery } from './useMediaQuery'

function Probe({ query }: { query: string }) {
  const matches = useMediaQuery(query)
  return <span>{String(matches)}</span>
}

describe('useMediaQuery', () => {
  it('returns current match state', () => {
    const matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query.includes('min-width'),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    }))
    Object.defineProperty(window, 'matchMedia', { value: matchMedia, writable: true })

    render(<Probe query="(min-width: 1024px)" />)
    expect(screen.getByText('true')).toBeTruthy()
  })
})
