import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Modal } from '../../../shared/components/Modal'
import { Input } from '../../../shared/components/Input'
import { Button } from '../../../shared/components/Button'
import { ErrorState } from '../../../shared/components/ErrorState'
import { createWorkspaceInvitation } from '../../workspace-members/workspace-members-api'
import type { WorkspaceRole } from '../../workspace-members/workspace-members-types'

const ROLES: WorkspaceRole[] = ['ADMIN', 'MEMBER', 'VIEWER']

type InviteMemberModalProps = {
  open: boolean
  workspaceId: string
  onClose: () => void
  onSuccess?: () => void
}

export function InviteMemberModal({ open, workspaceId, onClose, onSuccess }: InviteMemberModalProps) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<WorkspaceRole>('MEMBER')

  const mutation = useMutation({
    mutationFn: () => createWorkspaceInvitation(workspaceId, { email: email.trim(), role }),
    onSuccess: () => {
      setEmail('')
      onSuccess?.()
      onClose()
    },
  })

  return (
    <Modal open={open} title="Invite member" onClose={onClose}>
      <div className="space-y-3">
        <label className="block text-label-md text-on-surface-variant">
          Email
          <Input
            type="email"
            className="mt-1"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="colleague@company.com"
            autoComplete="email"
          />
        </label>
        <label className="block text-label-md text-on-surface-variant">
          Role
          <select
            className="mt-1 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-body-md"
            value={role}
            onChange={(e) => setRole(e.target.value as WorkspaceRole)}
          >
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </label>
        {mutation.isError ? <ErrorState error={mutation.error} /> : null}
        <Button
          type="button"
          className="w-full bg-primary text-white hover:bg-primary-container"
          disabled={!email.trim() || mutation.isPending}
          onClick={() => mutation.mutate()}
        >
          Send invitation
        </Button>
      </div>
    </Modal>
  )
}
