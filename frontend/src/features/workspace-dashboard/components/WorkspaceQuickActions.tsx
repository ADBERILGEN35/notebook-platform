import type { ReactNode } from 'react'

type QuickActionItem = {
  id: string
  title: string
  description: string
  tone: 'primary' | 'secondary'
  icon: ReactNode
  disabled?: boolean
  onClick?: () => void
}

type WorkspaceQuickActionsProps = {
  focusWorkspaceId: string | null
  onQuickNote: () => void
  onInviteMember?: () => void
  className?: string
  title?: string
}

const TONE_CLASSES: Record<QuickActionItem['tone'], string> = {
  primary: 'bg-primary/10 text-primary group-hover:bg-primary group-hover:text-on-primary',
  secondary: 'bg-secondary/10 text-secondary group-hover:bg-secondary group-hover:text-on-secondary',
}

/**
 * Compact "Quick Actions" widget that matches the Stitch design column.
 *
 * Production path shows only navigation/disabled actions. No mock data.
 */
export function WorkspaceQuickActions({
  focusWorkspaceId,
  onQuickNote,
  onInviteMember,
  className = '',
  title = 'Quick Actions',
}: WorkspaceQuickActionsProps) {
  const items: QuickActionItem[] = [
    {
      id: 'quick-note',
      title: 'Quick Note',
      description: focusWorkspaceId
        ? 'Jot down a fleeting thought'
        : 'Create a workspace first',
      tone: 'primary',
      icon: <span aria-hidden>✎</span>,
      disabled: !focusWorkspaceId,
      onClick: onQuickNote,
    },
    {
      id: 'invite-member',
      title: 'Invite Member',
      description: focusWorkspaceId
        ? 'Collaborate with others'
        : 'Available after workspace setup',
      tone: 'secondary',
      icon: <span aria-hidden>＋</span>,
      disabled: !focusWorkspaceId,
      onClick: onInviteMember,
    },
  ]

  return (
    <section
      className={`flex h-full flex-col rounded-2xl border border-outline-variant bg-surface-container-lowest p-5 shadow-card ${className}`}
      aria-labelledby="workspace-quick-actions-title"
    >
      <h2
        id="workspace-quick-actions-title"
        className="border-b border-outline-variant pb-3 font-display text-headline-sm text-on-surface"
      >
        {title}
      </h2>
      <ul className="mt-3 flex flex-1 flex-col justify-center gap-2">
        {items.map((item) => (
          <li key={item.id}>
            <button
              type="button"
              disabled={item.disabled}
              onClick={item.onClick}
              className="group flex w-full items-center gap-3 rounded-xl border border-transparent p-3 text-left transition-all hover:border-outline-variant hover:bg-surface-container-low focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60"
              aria-label={`${item.title} — ${item.description}`}
            >
              <span
                className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-headline-sm transition-colors ${TONE_CLASSES[item.tone]}`}
              >
                {item.icon}
              </span>
              <span className="flex flex-col">
                <span className="font-medium text-on-surface">{item.title}</span>
                <span className="text-label-md text-on-surface-variant">{item.description}</span>
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
