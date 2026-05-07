import { expect, type Page } from '@playwright/test'

export async function createNotebookFromShell(page: Page, notebookName: string) {
  await page.getByRole('button', { name: 'Create note' }).click()
  await page.getByRole('heading', { name: 'Create notebook' }).waitFor()
  const input = page.locator('input').last()
  await input.fill(notebookName)
  await page.getByRole('button', { name: 'Create' }).click()
  await expect(page).toHaveURL(/\/app\/notebooks\//)
}

