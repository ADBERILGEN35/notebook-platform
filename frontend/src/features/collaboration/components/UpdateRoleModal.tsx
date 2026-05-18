import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Modal } from '../../../shared/components/Modal'
import { Button } from '../../../shared/components/Button'
import { ErrorState } from '../../../shared/components/ErrorState'
import { updateWorkspaceMemberRole } from '../../workspace-members/workspace-members-api'
import type { WorkspaceRole } from '../../workspace-members/workspace-members-types'

const ROLES: WorkspaceRole[] = ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER']

type UpdateRoleModalProps = {
  open: boolean
  workspaceId: string
  userId: string
  memberLabel: string
  currentRole: WorkspaceRole
  onClose: () => void
  onSuccess?: () => void
}

export function UpdateRoleModal({
  open,
  workspaceId,
  userId,
  memberLabel,
  currentRole,
  onClose,
  onSuccess,
}: UpdateRoleModalProps) {
  const [role, setRole] = useState<WorkspaceRole>(currentRole)

  const mutation = useMutation({
    mutationFn: () => updateWorkspaceMemberRole(workspaceId, userId, role),
    onSuccess: () => {
      onSuccess?.()
      onClose()
    },
  })

  return (
    <Modal open={open} title="Update role" onClose={onClose}>
      <p className="mb-3 text-body-md text-on-surface-variant">Change role for {memberLabel}</p>
      <select
        className="w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-body-md"
        value={role}
        onChange={(e) => setRole(e.target.value as WorkspaceRole)}
        aria-label="Member role"
      >
        {ROLES.map((r) => (
          <option key={r} value={r}>
            {r}
          </option>
        ))}
      </select>
      {mutation.isError ? <ErrorState error={mutation.error} className="mt-3" /> : null}
      <Button
        type="button"
        className="mt-4 w-full bg-primary text-white hover:bg-primary-container"
        disabled={role === currentRole || mutation.isPending}
        onClick={() => mutation.mutate()}
      >
        Save role
      </Button>
    </Modal>
  )
}
