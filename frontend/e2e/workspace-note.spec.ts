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
  await page.getByTestId('conflict-review-button').click()
  await expect(page.getByTestId('conflict-dialog')).toBeVisible()
  await expect(page.getByTestId('conflict-overwrite')).toBeVisible()
  await expect(page.getByText('What changed?')).toBeVisible({ timeout: 15_000 })
})

test('conflict overwrite retries with latest etag', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)
  await createWorkspace(page, data.workspaceName)
  await createNotebookFromShell(page, data.notebookName)
  await createNote(page)
  await page.getByTestId('autosave-indicator').waitFor()

  let patchCount = 0
  let lastIfMatch = ''
  await page.route('**/notes/*', async (route) => {
    const method = route.request().method()
    if (method === 'PATCH') {
      patchCount += 1
      if (patchCount === 1) {
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
      lastIfMatch = route.request().headers()['if-match'] || ''
      await route.fulfill({
        status: 200,
        headers: { ETag: '"note-rev-latest"' },
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'overwrite-note',
          workspaceId: 'ws',
          notebookId: 'nb',
          title: `${data.noteTitle}-overwrite`,
          contentBlocks: [],
          contentSchemaVersion: 1,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
      })
      return
    }
    if (method === 'GET') {
      await route.fulfill({
        status: 200,
        headers: { ETag: '"note-rev-latest"' },
        contentType: 'application/json',
        body: JSON.stringify({
          id: 'overwrite-note',
          workspaceId: 'ws',
          notebookId: 'nb',
          title: 'Server latest title',
          contentBlocks: [],
          contentSchemaVersion: 1,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
      })
      return
    }
    await route.continue()
  })

  await updateNote(page, `${data.noteTitle}-overwrite`, data.noteContent)
  await page.getByTestId('conflict-review-button').click()
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByTestId('conflict-overwrite').click()
  await expect.poll(() => lastIfMatch).toContain('note-rev-latest')
})

