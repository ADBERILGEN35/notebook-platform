import { useQuery } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router-dom'
import React from 'react'
import { getNotebook } from '../features/notebooks/notebook-api'
import { createNote, listNotes } from '../features/notes/note-api'
import { useMutation } from '@tanstack/react-query'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { LoadingState } from '../shared/components/LoadingState'
import { EmptyState } from '../shared/components/EmptyState'
import { Button } from '../shared/components/Button'
import { PageHeader } from '../shared/components/PageHeader'
import { PaginationControls } from '../shared/components/PaginationControls'
import { createParagraphBlock } from '../shared/utils/blocks'

export function NotebookPage() {
  const { notebookId } = useParams()
  const navigate = useNavigate()
  const [page, setPage] = React.useState(0)
  const notebookQuery = useQuery({
    queryKey: ['notebook', notebookId],
    queryFn: () => getNotebook(notebookId!),
    enabled: Boolean(notebookId),
  })
  const notesQuery = useQuery({
    queryKey: ['notes', notebookId, page],
    queryFn: () => listNotes(notebookId!, page, 20),
    enabled: Boolean(notebookId),
  })
  const createMutation = useMutation({
    mutationFn: () => createNote(notebookId!, { title: 'Untitled note', contentBlocks: [createParagraphBlock()] }),
    onSuccess: (note) => navigate(`/app/notes/${note.id}`),
  })

  if (notebookQuery.isLoading || notesQuery.isLoading) return <LoadingState />
  if (notebookQuery.isError) return <ErrorAlert error={notebookQuery.error} />
  if (notesQuery.isError) return <ErrorAlert error={notesQuery.error} />

  const pageData = notesQuery.data
  return (
    <div className="space-y-3">
      <PageHeader title={notebookQuery.data?.name || 'Notebook'} subtitle="Notes in selected notebook" />
      <Button className="bg-primary-600 text-white hover:bg-primary-700" onClick={() => createMutation.mutate()}>
        Create note
      </Button>
      {!pageData?.items.length ? (
        <EmptyState title="No notes" message="Create the first note in this notebook." />
      ) : (
        <div className="space-y-2">
          {pageData.items.map((note) => (
            <button
              key={note.id}
              onClick={() => navigate(`/app/notes/${note.id}`)}
              className="block w-full rounded border border-slate-200 bg-white px-3 py-2 text-left text-sm"
            >
              {note.title}
            </button>
          ))}
          <PaginationControls
            page={pageData.page}
            hasNext={pageData.hasNext}
            hasPrevious={pageData.hasPrevious}
            onNext={() => setPage((current) => current + 1)}
            onPrevious={() => setPage((current) => Math.max(0, current - 1))}
          />
        </div>
      )}
    </div>
  )
}

