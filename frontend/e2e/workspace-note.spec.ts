import { expect, test } from '@playwright/test'
import { createE2eData } from './helpers/test-data'
import { signUpAndLogin } from './helpers/auth.helper'
import { createWorkspace } from './helpers/workspace.helper'
import { createNotebookFromShell } from './helpers/notebook.helper'
import {
  createNote,
  expectBlockNoteContains,
  expectSaveStatus,
  updateNote,
  waitForAutoSave,
} from './helpers/note.helper'

test('workspace -> notebook -> note core journey', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)
  await createWorkspace(page, data.workspaceName)
  await createNotebookFromShell(page, data.notebookName)
  await createNote(page)
  await page.getByTestId('autosave-indicator').waitFor()
  await updateNote(page, data.noteTitle, data.noteContent)
  await waitForAutoSave(page)
  await expectSaveStatus(page, /saved/i)

  await page.reload()
  await expect(page.getByTestId('note-title-input')).toHaveValue(data.noteTitle)
  await expectBlockNoteContains(page, data.noteContent)
  await expect(page.getByTestId('save-status')).toContainText(/saved|unsaved/i)

  await expect(page.getByText('Search')).toBeVisible()
})

test('autosave conflict shows reload latest banner on 412', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)
  await createWorkspace(page, data.workspaceName)
  await createNotebookFromShell(page, data.notebookName)
  await createNote(page)
  await page.getByTestId('autosave-indicator').waitFor()

  await page.route('**/notes/*', async (route) => {
    if (route.request().method() === 'PATCH') {
      await route.fulfill({
        status: 412,
        contentType: 'application/json',
        body: JSON.stringify({
          timestamp: new Date().toISOString(),
          status: 412,
          errorCode: 'NOTE_CONFLICT',
          message: 'Note changed on server',
          path: '/notes/conflict',
        }),
      })
      return
    }
    await route.continue()
  })

  await updateNote(page, `${data.noteTitle}-conflict`, data.noteContent)
  await expect(page.getByTestId('conflict-banner')).toBeVisible()
  await expect(page.getByTestId('save-status')).toContainText(/conflict/i)
})

