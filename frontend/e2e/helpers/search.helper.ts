import { expect, type Page } from '@playwright/test'

export async function searchNote(page: Page, query: string) {
  await page.goto('/app/search')
  await page.getByPlaceholder('Type at least 2 chars...').fill(query)
}

export async function waitForSearchResult(page: Page, title: string) {
  await expect
    .poll(
      async () => {
        await page.reload()
        await page.getByPlaceholder('Type at least 2 chars...').fill(title)
        return page.getByRole('heading', { name: title }).count()
      },
      { timeout: 30_000, intervals: [1000, 2000, 3000] }
    )
    .toBeGreaterThan(0)
}

