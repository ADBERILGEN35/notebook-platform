import { expect, type Page } from '@playwright/test'

export async function createNote(page: Page) {
  await page.getByRole('button', { name: 'Create note' }).click()
  await expect(page).toHaveURL(/\/app\/notes\//)
}

export async function updateNote(page: Page, title: string, content: string) {
  await page.getByTestId('note-title-input').fill(title)
  await fillBlockNoteEditor(page, content)
  await page.getByTestId('note-save-button').click()
}

export async function fillBlockNoteEditor(page: Page, text: string) {
  const editor = page.getByTestId('blocknote-editor')
  await editor.click()
  await page.keyboard.press(process.platform === 'darwin' ? 'Meta+A' : 'Control+A')
  await page.keyboard.type(text)
}

export async function expectBlockNoteContains(page: Page, text: string) {
  await expect(page.getByTestId('blocknote-editor')).toContainText(text)
}

export async function waitForAutoSave(page: Page) {
  await expect(page.getByTestId('save-status')).toContainText(/saved/i, {
    timeout: 15_000,
  })
}

export async function expectSaveStatus(page: Page, statusPattern: RegExp) {
  await expect(page.getByTestId('save-status')).toContainText(statusPattern)
}

