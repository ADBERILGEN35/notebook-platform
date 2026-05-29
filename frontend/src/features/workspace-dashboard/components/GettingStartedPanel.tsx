import { useState } from 'react'
import { Button } from '../../../shared/components/Button'
import { ONBOARDING_STEPS, type OnboardingStepId } from '../../onboarding/onboarding-storage'

type GettingStartedPanelProps = {
  hasWorkspace: boolean
  onDismiss: () => void
  onFocusCreate: () => void
  focusCreateRef?: { current: HTMLInputElement | null }
}

const STEP_DESCRIPTIONS: Record<OnboardingStepId, { title: string; description: string }> = {
  welcome: {
    title: 'Sign-in verified',
    description: 'Your identity provider session is active.',
  },
  workspace: {
    title: 'Create your workspace',
    description: 'Name a workspace to organize notebooks and notes.',
  },
  notebook: {
    title: 'Add your first notebook',
    description: 'Notebooks group related notes — add one from the sidebar after setup.',
  },
  'first-note': {
    title: 'Write your first note',
    description: 'Open a notebook to start writing. Sync uses the connected backend.',
  },
}

/**
 * Embedded onboarding panel for the dashboard. Replaces the previous
 * full-page wizard so the design's dashboard layout (hero, quick actions,
 * recently viewed) stays visible while we guide the user.
 *
 * Uses existing localStorage decision via onDismiss() from parent.
 * No new backend onboarding service is introduced.
 */
export function GettingStartedPanel({
  hasWorkspace,
  onDismiss,
  onFocusCreate,
}: GettingStartedPanelProps) {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <section
      data-testid="getting-started-panel"
      className="rounded-2xl border border-primary/20 bg-primary-fixed/40 p-5 shadow-sm"
      aria-labelledby="getting-started-panel-title"
    >
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-label-md font-medium uppercase tracking-wide text-primary">
            Get started
          </p>
          <h2
            id="getting-started-panel-title"
            className="mt-1 font-display text-headline-sm text-on-surface"
          >
            Three quick steps to your first note
          </h2>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" onClick={() => setCollapsed((value) => !value)}>
            {collapsed ? 'Show steps' : 'Hide steps'}
          </Button>
          <Button type="button" onClick={onDismiss} aria-label="Dismiss onboarding for this device">
            Dismiss
          </Button>
        </div>
      </header>

      {collapsed ? null : (
        <ol className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {ONBOARDING_STEPS.map((id, index) => {
            const meta = STEP_DESCRIPTIONS[id]
            const isWorkspaceStep = id === 'workspace'
            const isComplete = id === 'welcome' || (id === 'workspace' && hasWorkspace)
            return (
              <li
                key={id}
                className="flex flex-col gap-1 rounded-xl border border-outline-variant bg-surface-container-lowest p-3"
              >
                <div className="flex items-center gap-2">
                  <span
                    className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-label-md font-medium ${
                      isComplete
                        ? 'bg-primary text-on-primary'
                        : 'bg-surface-container-high text-on-surface-variant'
                    }`}
                    aria-hidden
                  >
                    {isComplete ? '✓' : index + 1}
                  </span>
                  <span className="font-medium text-on-surface">{meta.title}</span>
                </div>
                <p className="text-label-md text-on-surface-variant">{meta.description}</p>
                {isWorkspaceStep && !hasWorkspace ? (
                  <button
                    type="button"
                    onClick={onFocusCreate}
                    className="mt-1 self-start text-label-md font-medium text-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                  >
                    Focus create form
                  </button>
                ) : null}
              </li>
            )
          })}
        </ol>
      )}
    </section>
  )
}
