import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '../shared/api/api-client'
import { listWorkspaceInvitations, listWorkspaceMembers } from '../features/workspace-members/workspace-members-api'
import type { WorkspaceMember } from '../features/workspace-members/workspace-members-types'
import { PageHeader } from '../shared/components/PageHeader'
import { ResponsiveContent } from '../shared/components/ResponsiveContent'
import { SectionCard } from '../shared/components/SectionCard'
import { MemberRow } from '../shared/components/MemberRow'
import { LoadingState } from '../shared/components/LoadingState'
import { ErrorState } from '../shared/components/ErrorState'
import { EmptyState } from '../shared/components/EmptyState'
import { Button } from '../shared/components/Button'
import { RoleBadge } from '../shared/components/RoleBadge'
import { AccessDeniedState } from '../features/access/components/AccessDeniedState'
import { InviteMemberModal } from '../features/collaboration/components/InviteMemberModal'
import { UpdateRoleModal } from '../features/collaboration/components/UpdateRoleModal'
import { RevokeAccessConfirmModal } from '../features/collaboration/components/RevokeAccessConfirmModal'

export function WorkspaceMembersPage() {
  const { workspaceId } = useParams()
  const [inviteOpen, setInviteOpen] = useState(false)
  const [roleTarget, setRoleTarget] = useState<WorkspaceMember | null>(null)
  const [revokeTarget, setRevokeTarget] = useState<{ mode: 'member' | 'invitation'; id: string; label: string } | null>(
    null,
  )

  const membersQuery = useQuery({
    queryKey: ['workspace-members', workspaceId],
    queryFn: () => listWorkspaceMembers(workspaceId!, 0, 100),
    enabled: Boolean(workspaceId),
  })

  const invitationsQuery = useQuery({
    queryKey: ['workspace-invitations', workspaceId],
    queryFn: () => listWorkspaceInvitations(workspaceId!, 0, 20),
    enabled: Boolean(workspaceId),
  })

  if (!workspaceId) return null

  if (membersQuery.isError && membersQuery.error instanceof ApiError && membersQuery.error.status === 403) {
    return (
      <ResponsiveContent>
        <AccessDeniedState message="You do not have permission to view members for this workspace." />
      </ResponsiveContent>
    )
  }

  return (
    <ResponsiveContent>
      <PageHeader
        title="Members & access"
        subtitle="Manage workspace membership, roles, and pending invitations."
        actions={
          <Button type="button" className="bg-primary text-white hover:bg-primary-container" onClick={() => setInviteOpen(true)}>
            Invite member
          </Button>
        }
      />
      <nav className="mb-4 text-label-md">
        <Link to={`/app/workspaces/${workspaceId}`} className="text-primary hover:underline">
          ← Back to workspace hub
        </Link>
        <span className="mx-2 text-on-surface-variant">·</span>
        <Link to={`/app/workspaces/${workspaceId}/settings`} className="text-primary hover:underline">
          Settings
        </Link>
      </nav>

      {membersQuery.isLoading ? <LoadingState label="Loading members…" /> : null}
      {membersQuery.isError ? <ErrorState error={membersQuery.error} /> : null}

      {membersQuery.data ? (
        <SectionCard title="Members" description={`${membersQuery.data.items.length} people with access`}>
          {membersQuery.data.items.length === 0 ? (
            <EmptyState title="No members" message="Invite teammates to collaborate in this workspace." />
          ) : (
            <ul className="space-y-2">
              {membersQuery.data.items.map((member) => (
                <MemberRow
                  key={member.userId}
                  name={`User ${member.userId.slice(0, 8)}`}
                  subtitle={`Joined ${new Date(member.joinedAt).toLocaleDateString()}`}
                  role={member.role}
                  actions={
                    <>
                      <Button type="button" className="text-label-md" onClick={() => setRoleTarget(member)}>
                        Change role
                      </Button>
                      <Button type="button" className="text-label-md" onClick={() => setRevokeTarget({ mode: 'member', id: member.userId, label: member.userId.slice(0, 8) })}>
                        Remove
                      </Button>
                    </>
                  }
                />
              ))}
            </ul>
          )}
        </SectionCard>
      ) : null}

      <SectionCard title="Pending invitations" description="Invites awaiting acceptance" className="mt-6">
        {invitationsQuery.isLoading ? <LoadingState label="Loading invitations…" /> : null}
        {invitationsQuery.isError ? <ErrorState error={invitationsQuery.error} /> : null}
        {invitationsQuery.data?.items.length === 0 && !invitationsQuery.isLoading ? (
          <EmptyState title="No pending invitations" message="Invite someone to get started." />
        ) : null}
        {invitationsQuery.data?.items.length ? (
          <ul className="space-y-2">
            {invitationsQuery.data.items
              .filter((inv) => !inv.acceptedAt && !inv.revokedAt)
              .map((inv) => (
                <li
                  key={inv.id}
                  className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-outline-variant px-4 py-3"
                >
                  <div>
                    <p className="font-medium text-on-surface">{inv.email}</p>
                    <p className="text-label-md text-on-surface-variant">
                      Expires {new Date(inv.expiresAt).toLocaleDateString()}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <RoleBadge role={inv.role} />
                    <Button
                      type="button"
                      onClick={() => setRevokeTarget({ mode: 'invitation', id: inv.id, label: inv.email })}
                    >
                      Revoke
                    </Button>
                  </div>
                </li>
              ))}
          </ul>
        ) : null}
      </SectionCard>

      <InviteMemberModal
        open={inviteOpen}
        workspaceId={workspaceId}
        onClose={() => setInviteOpen(false)}
        onSuccess={() => {
          void membersQuery.refetch()
          void invitationsQuery.refetch()
        }}
      />
      {roleTarget ? (
        <UpdateRoleModal
          open
          workspaceId={workspaceId}
          userId={roleTarget.userId}
          memberLabel={roleTarget.userId.slice(0, 8)}
          currentRole={roleTarget.role}
          onClose={() => setRoleTarget(null)}
          onSuccess={() => void membersQuery.refetch()}
        />
      ) : null}
      {revokeTarget ? (
        <RevokeAccessConfirmModal
          open
          mode={revokeTarget.mode}
          workspaceId={workspaceId}
          targetId={revokeTarget.id}
          targetLabel={revokeTarget.label}
          onClose={() => setRevokeTarget(null)}
          onSuccess={() => {
            void membersQuery.refetch()
            void invitationsQuery.refetch()
          }}
        />
      ) : null}
    </ResponsiveContent>
  )
}
