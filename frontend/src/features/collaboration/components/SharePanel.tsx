import { PanelCard } from '../../../shared/components/PanelCard'
import { CollaboratorAvatarStack } from '../../../shared/components/CollaboratorAvatarStack'
import { Button } from '../../../shared/components/Button'
import { RoleBadge } from '../../../shared/components/RoleBadge'
import type { WorkspaceMember } from '../../workspace-members/workspace-members-types'

type SharePanelProps = {
  members: WorkspaceMember[]
  onInvite?: () => void
  onManageMembers?: () => void
}

export function SharePanel({ members, onInvite, onManageMembers }: SharePanelProps) {
  const collaborators = members.map((m) => ({ id: m.userId, label: m.userId.slice(0, 8) }))

  return (
    <PanelCard title="Share & collaborate" subtitle="Workspace access">
      <CollaboratorAvatarStack collaborators={collaborators} />
      <ul className="mt-4 space-y-2">
        {members.slice(0, 6).map((member) => (
          <li key={member.userId} className="flex items-center justify-between gap-2 text-body-md">
            <span className="truncate text-on-surface">Member {member.userId.slice(0, 8)}…</span>
            <RoleBadge role={member.role} />
          </li>
        ))}
      </ul>
      <p className="mt-3 text-label-md text-on-surface-variant">
        Note-level sharing uses workspace membership. Invite collaborators to the workspace to grant access.
      </p>
      <div className="mt-4 flex flex-wrap gap-2">
        {onInvite ? (
          <Button type="button" className="bg-primary text-white hover:bg-primary-container" onClick={onInvite}>
            Invite
          </Button>
        ) : null}
        {onManageMembers ? (
          <Button type="button" onClick={onManageMembers}>
            Manage members
          </Button>
        ) : null}
      </div>
    </PanelCard>
  )
}
