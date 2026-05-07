import { expect, test } from '@playwright/test'
import { createE2eData } from './helpers/test-data'
import { signUpAndLogin } from './helpers/auth.helper'
import { createWorkspace } from './helpers/workspace.helper'
import { createNotebookFromShell } from './helpers/notebook.helper'
import { createNote, expectBlockNoteContains, updateNote, waitForAutoSave } from './helpers/note.helper'

test('comments and versions smoke journey', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)
  await createWorkspace(page, data.workspaceName)
  await createNotebookFromShell(page, data.notebookName)
  await createNote(page)
  await updateNote(page, data.noteTitle, data.noteContent)
  await waitForAutoSave(page)

  await page.getByPlaceholder('Add a note-level comment...').fill(`Comment ${data.suffix}`)
  await page.getByRole('button', { name: 'Add comment' }).click()
  await expect(page.getByText(`Comment ${data.suffix}`)).toBeVisible()
  await page.getByRole('button', { name: 'Resolve' }).click()
  await expect(page.getByRole('button', { name: 'Reopen' })).toBeVisible()
  await page.getByRole('button', { name: 'Reopen' }).click()

  await page.getByRole('button', { name: 'versions' }).click()
  await expect(page.getByText(/^v\d+/)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Restore' })).toBeVisible()
  await page.getByRole('button', { name: 'Restore' }).first().click()
  await waitForAutoSave(page)
  await expectBlockNoteContains(page, data.noteContent)
})

