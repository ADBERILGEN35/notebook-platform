import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getNote } from '../features/notes/note-api'
import { listWorkspaceMembers } from '../features/workspace-members/workspace-members-api'
import { NoteEditorShell } from '../features/note-editor/components/NoteEditorShell'
import { NoteEditorHeader } from '../features/note-editor/components/NoteEditorHeader'
import { SharePanel } from '../features/collaboration/components/SharePanel'
import { InviteMemberModal } from '../features/collaboration/components/InviteMemberModal'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { NotePage } from './NotePage'

export function NoteEditorPage() {
  const { workspaceId, noteId } = useParams()
  const navigate = useNavigate()
  const [inviteOpen, setInviteOpen] = useState(false)
  const [showSharePanel, setShowSharePanel] = useState(true)

  const noteQuery = useQuery({
    queryKey: ['note', noteId],
    queryFn: () => getNote(noteId!),
    enabled: Boolean(noteId),
  })

  const membersQuery = useQuery({
    queryKey: ['workspace-members', workspaceId],
    queryFn: () => listWorkspaceMembers(workspaceId!, 0, 50),
    enabled: Boolean(workspaceId),
  })

  if (!workspaceId || !noteId) {
    return null
  }

  const saveTone =
    noteQuery.isError ? 'warning' : noteQuery.isSuccess ? 'success' : ('neutral' as const)

  return (
    <ResponsiveContent>
      <NoteEditorShell
        header={
          <NoteEditorHeader
            workspaceId={workspaceId}
            noteTitle={noteQuery.data?.note.title}
            saveStatusLabel={noteQuery.isLoading ? 'Loading…' : undefined}
            saveTone={saveTone}
            onShare={() => setShowSharePanel((v) => !v)}
            onHistory={() => navigate(`/app/workspaces/${workspaceId}/notes/${noteId}/history`)}
          />
        }
        sidePanel={
          showSharePanel ? (
            <SharePanel
              members={membersQuery.data?.items ?? []}
              onInvite={() => setInviteOpen(true)}
              onManageMembers={() => navigate(`/app/workspaces/${workspaceId}/members`)}
            />
          ) : undefined
        }
        sidePanelOpen={showSharePanel}
      >
        <NotePage embedded expectedWorkspaceId={workspaceId} />
      </NoteEditorShell>
      <InviteMemberModal
        open={inviteOpen}
        workspaceId={workspaceId}
        onClose={() => setInviteOpen(false)}
        onSuccess={() => void membersQuery.refetch()}
      />
    </ResponsiveContent>
  )
}
