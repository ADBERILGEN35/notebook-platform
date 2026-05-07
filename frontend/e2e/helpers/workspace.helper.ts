import { expect, type Page } from '@playwright/test'

export async function createWorkspace(page: Page, workspaceName: string) {
  await page.goto('/app')
  await page.getByPlaceholder('Create new workspace').fill(workspaceName)
  await page.getByRole('button', { name: 'Create workspace' }).click()
  await expect(page.getByText('Workspace')).toBeVisible()
}

