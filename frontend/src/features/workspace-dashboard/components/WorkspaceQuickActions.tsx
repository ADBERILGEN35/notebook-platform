import { QuickActionCard } from '../../../shared/components/QuickActionCard'

type WorkspaceQuickActionsProps = {
  focusWorkspaceId: string | null
  onSearch: () => void
  onNotifications: () => void
  onOpenWorkspace?: () => void
  className?: string
  title?: string
}

export function WorkspaceQuickActions({
  focusWorkspaceId,
  onSearch,
  onNotifications,
  onOpenWorkspace,
  className = '',
  title = 'Quick actions',
}: WorkspaceQuickActionsProps) {
  return (
    <section className={className} aria-labelledby="workspace-quick-actions-title">
      <h2 id="workspace-quick-actions-title" className="font-display text-headline-sm text-on-surface">
        {title}
      </h2>
      <ul className="mt-3 flex flex-col gap-2">
        <li>
          <QuickActionCard
            title="Quick note"
            description={
              focusWorkspaceId
                ? 'Open search to find or start content in your workspace.'
                : 'Create a workspace first, then capture notes from the editor.'
            }
            icon={<span aria-hidden>📝</span>}
            disabled={!focusWorkspaceId}
            onClick={onSearch}
          />
        </li>
        <li>
          <QuickActionCard
            title="Invite member"
            description="Manage members from workspace settings after setup."
            icon={<span aria-hidden>👥</span>}
            disabled={!focusWorkspaceId}
            onClick={onOpenWorkspace}
          />
        </li>
        <li>
          <QuickActionCard
            title="Notifications"
            description="Review mentions and workspace activity."
            icon={<span aria-hidden>🔔</span>}
            onClick={onNotifications}
          />
        </li>
      </ul>
    </section>
  )
}
