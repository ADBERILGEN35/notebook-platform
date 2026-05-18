import { useMutation } from '@tanstack/react-query'
import { ConfirmActionModal } from '../../../shared/components/ConfirmActionModal'
import { ErrorState } from '../../../shared/components/ErrorState'
import { removeWorkspaceMember, revokeWorkspaceInvitation } from '../../workspace-members/workspace-members-api'

type RevokeAccessConfirmModalProps = {
  open: boolean
  mode: 'member' | 'invitation'
  workspaceId: string
  targetId: string
  targetLabel: string
  onClose: () => void
  onSuccess?: () => void
}

export function RevokeAccessConfirmModal({
  open,
  mode,
  workspaceId,
  targetId,
  targetLabel,
  onClose,
  onSuccess,
}: RevokeAccessConfirmModalProps) {
  const mutation = useMutation({
    mutationFn: async () => {
      if (mode === 'member') {
        await removeWorkspaceMember(workspaceId, targetId)
      } else {
        await revokeWorkspaceInvitation(targetId)
      }
    },
    onSuccess: () => {
      onSuccess?.()
      onClose()
    },
  })

  return (
    <>
      <ConfirmActionModal
        open={open}
        title={mode === 'member' ? 'Remove member' : 'Revoke invitation'}
        message={
          mode === 'member'
            ? `Remove ${targetLabel} from this workspace? They will lose access immediately.`
            : `Revoke the pending invitation for ${targetLabel}?`
        }
        confirmLabel={mode === 'member' ? 'Remove member' : 'Revoke invitation'}
        tone="danger"
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate()}
        onClose={onClose}
      />
      {mutation.isError && open ? <ErrorState error={mutation.error} className="mt-2" /> : null}
    </>
  )
}
