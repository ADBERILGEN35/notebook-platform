import { useState } from 'react'
import { Button } from '../../../shared/components/Button'
import { Input } from '../../../shared/components/Input'
import {
  markOnboardingComplete,
  ONBOARDING_STEPS,
  type OnboardingStepId,
} from '../onboarding-storage'

type OnboardingWizardProps = {
  workspaceName: string
  onWorkspaceNameChange: (value: string) => void
  onCreateWorkspace: () => void
  createPending: boolean
  createError?: boolean
  hasWorkspace: boolean
  onFinish: () => void
}

const STEP_LABELS: Record<OnboardingStepId, string> = {
  welcome: 'Welcome',
  workspace: 'Workspace',
  notebook: 'Notebook',
  'first-note': 'First note',
}

export function OnboardingWizard({
  workspaceName,
  onWorkspaceNameChange,
  onCreateWorkspace,
  createPending,
  createError,
  hasWorkspace,
  onFinish,
}: OnboardingWizardProps) {
  const [stepIndex, setStepIndex] = useState(0)
  const step = ONBOARDING_STEPS[stepIndex] ?? 'welcome'

  const goNext = () => setStepIndex((i) => Math.min(i + 1, ONBOARDING_STEPS.length - 1))
  const goBack = () => setStepIndex((i) => Math.max(i - 1, 0))

  const finish = () => {
    markOnboardingComplete()
    onFinish()
  }

  return (
    <section
      className="rounded-2xl border border-outline-variant bg-surface-container-lowest p-6 shadow-sm md:p-8"
      data-testid="onboarding-wizard"
      aria-labelledby="onboarding-title"
    >
      <nav aria-label="Onboarding progress" className="mb-6">
        <ol className="flex flex-wrap gap-2">
          {ONBOARDING_STEPS.map((id, index) => (
            <li
              key={id}
              className={`rounded-full px-3 py-1 text-label-md ${
                index === stepIndex
                  ? 'bg-primary text-on-primary'
                  : index < stepIndex
                    ? 'bg-primary-container/30 text-on-surface'
                    : 'bg-surface-container text-on-surface-variant'
              }`}
              aria-current={index === stepIndex ? 'step' : undefined}
            >
              {STEP_LABELS[id]}
            </li>
          ))}
        </ol>
      </nav>

      {step === 'welcome' ? (
        <>
          <h2 id="onboarding-title" className="font-display text-headline-sm text-on-surface">
            Welcome to Notebook Platform
          </h2>
          <p className="mt-2 text-body-md text-on-surface-variant">
            Verify your account is ready, then set up a workspace for your team. No credentials are shown in this flow.
          </p>
          <ul className="mt-4 list-inside list-disc text-body-md text-on-surface-variant">
            <li>Sign-in completed through your organization identity provider</li>
            <li>Session is active — you can proceed to workspace setup</li>
          </ul>
          <div className="mt-6">
            <Button type="button" className="bg-primary text-on-primary" onClick={goNext}>
              Continue
            </Button>
          </div>
        </>
      ) : null}

      {step === 'workspace' ? (
        <>
          <h2 id="onboarding-title" className="font-display text-headline-sm text-on-surface">
            Create your workspace
          </h2>
          <p className="mt-2 text-body-md text-on-surface-variant">
            Workspaces group notebooks and notes. Choose a name your team will recognize.
          </p>
          <div className="mt-4 flex max-w-md flex-col gap-2">
            <Input
              placeholder="e.g. Product Engineering"
              value={workspaceName}
              onChange={(e) => onWorkspaceNameChange(e.target.value)}
              aria-label="Workspace name"
              data-testid="onboarding-workspace-name"
            />
            <Button
              type="button"
              className="bg-primary text-on-primary"
              disabled={!workspaceName.trim() || createPending || hasWorkspace}
              onClick={onCreateWorkspace}
              data-testid="onboarding-create-workspace"
            >
              {hasWorkspace ? 'Workspace created' : createPending ? 'Creating…' : 'Create workspace'}
            </Button>
            {createError ? (
              <p className="text-body-sm text-error" role="alert">
                Could not create workspace. Try again or use support.
              </p>
            ) : null}
          </div>
          <div className="mt-6 flex flex-wrap gap-2">
            <Button type="button" onClick={goBack}>
              Back
            </Button>
            <Button type="button" className="bg-primary text-on-primary" disabled={!hasWorkspace} onClick={goNext}>
              Continue
            </Button>
          </div>
        </>
      ) : null}

      {step === 'notebook' ? (
        <>
          <h2 id="onboarding-title" className="font-display text-headline-sm text-on-surface">
            First notebook setup
          </h2>
          <p className="mt-2 text-body-md text-on-surface-variant">
            Notebooks organize related notes inside your workspace. Use the sidebar or workspace hub to add one when your
            API is connected.
          </p>
          <div className="mt-6 flex flex-wrap gap-2">
            <Button type="button" onClick={goBack}>
              Back
            </Button>
            <Button type="button" className="bg-primary text-on-primary" onClick={goNext}>
              Continue
            </Button>
          </div>
        </>
      ) : null}

      {step === 'first-note' ? (
        <>
          <h2 id="onboarding-title" className="font-display text-headline-sm text-on-surface">
            Your first note
          </h2>
          <p className="mt-2 text-body-md text-on-surface-variant">
            Open a notebook and create a note to start writing. Collaboration and sync use your connected backend when
            available.
          </p>
          <div className="mt-6 flex flex-wrap gap-2">
            <Button type="button" onClick={goBack}>
              Back
            </Button>
            <Button
              type="button"
              className="bg-primary text-on-primary"
              data-testid="onboarding-finish"
              onClick={finish}
            >
              Go to workspace hub
            </Button>
          </div>
        </>
      ) : null}
    </section>
  )
}
