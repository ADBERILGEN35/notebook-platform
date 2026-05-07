import { expect, test } from '@playwright/test'
import { createE2eData } from './helpers/test-data'
import { signUpAndLogin } from './helpers/auth.helper'
import { createWorkspace } from './helpers/workspace.helper'
import { createNotebookFromShell } from './helpers/notebook.helper'
import { createNote, updateNote } from './helpers/note.helper'
import { searchNote, waitForSearchResult } from './helpers/search.helper'

test('search finds newly created note with eventual consistency retry', async ({ page }) => {
  const data = createE2eData()
  await signUpAndLogin(page, data.email, data.password)
  await createWorkspace(page, data.workspaceName)
  await createNotebookFromShell(page, data.notebookName)
  await createNote(page)
  await updateNote(page, data.noteTitle, data.noteContent)
  await searchNote(page, data.searchQuery)
  await waitForSearchResult(page, data.noteTitle)
  await expect(page.getByRole('heading', { name: data.noteTitle })).toBeVisible()
})

