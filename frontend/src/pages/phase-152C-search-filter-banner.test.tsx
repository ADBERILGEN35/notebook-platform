import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { SearchResultsPage } from './SearchResultsPage'

vi.mock('../features/search/search-api', () => ({
  searchNotes: vi.fn(async () => ({
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  })),
}))

vi.mock('../features/workspaces/workspace-store', () => ({
  useWorkspaceStore: (selector: (s: { activeWorkspaceId: string }) => unknown) =>
    selector({ activeWorkspaceId: 'w1' }),
}))

const renderAt = (url: string) =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <QueryClientProvider client={new QueryClient()}>
        <Routes>
          <Route path="/app/search" element={<SearchResultsPage />} />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  )

describe('Faz 152C — SearchResultsPage filter banner', () => {
  it('shows InlineStatus + helper text when ?filter=drafts is present', () => {
    renderAt('/app/search?q=meeting&filter=drafts')
    const banner = screen.getByTestId('search-filter-notice')
    expect(banner).toBeTruthy()
    expect(banner.textContent ?? '').toMatch(/Filter:\s*drafts/i)
    expect(banner.textContent ?? '').toMatch(/backend filter endpoint is not yet available/i)
  })

  it('shows banner for "shared" filter', () => {
    renderAt('/app/search?filter=shared')
    expect(screen.getByTestId('search-filter-notice').textContent ?? '').toMatch(/shared/i)
  })

  it('shows banner for "archived" filter', () => {
    renderAt('/app/search?filter=archived')
    expect(screen.getByTestId('search-filter-notice').textContent ?? '').toMatch(/archived/i)
  })

  it('does not render the banner when no filter param is present', () => {
    renderAt('/app/search?q=meeting')
    expect(screen.queryByTestId('search-filter-notice')).toBeNull()
  })

  it('ignores unknown filter values (no banner)', () => {
    renderAt('/app/search?filter=bogus')
    expect(screen.queryByTestId('search-filter-notice')).toBeNull()
  })

  it('Clear filter button removes the filter param', () => {
    renderAt('/app/search?q=meeting&filter=drafts')
    fireEvent.click(screen.getByRole('button', { name: /clear filter/i }))
    expect(screen.queryByTestId('search-filter-notice')).toBeNull()
  })

  it('does not leak tokens / Bearer headers in markup', () => {
    const { container } = renderAt('/app/search?q=meeting&filter=drafts')
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/)
    expect(text).not.toMatch(/Bearer\s+[A-Za-z0-9._-]+/)
    expect(text).not.toMatch(/Authorization:/i)
  })
})
