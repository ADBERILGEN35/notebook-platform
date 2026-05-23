import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { OnboardingWizard } from '../features/onboarding/components/OnboardingWizard'
import { GitOpsDiffViewer } from '../features/admin/change-requests/GitOpsDiffViewer'
import { NoteConflictResolutionDialog } from '../features/notes/components/NoteConflictResolutionDialog'
import { resetOnboardingForTests } from '../features/onboarding/onboarding-storage'
const diff = `--- a/config.yaml
+++ b/config.yaml
@@ -1 +1 @@
-old
+new
`

describe('Faz 150 design reconciliation', () => {
  beforeEach(() => {
    resetOnboardingForTests()
  })

  it('renders onboarding wizard welcome step', () => {
    render(
      <OnboardingWizard
        workspaceName=""
        onWorkspaceNameChange={vi.fn()}
        onCreateWorkspace={vi.fn()}
        createPending={false}
        hasWorkspace={false}
        onFinish={vi.fn()}
      />,
    )
    expect(screen.getByTestId('onboarding-wizard')).toBeTruthy()
    expect(screen.getByText(/Welcome to Notebook Platform/i)).toBeTruthy()
  })

  it('renders gitops unified diff with a11y region', () => {
    render(<GitOpsDiffViewer diffPreview={diff} mode="unified" compact />)
    expect(screen.getByTestId('gitops-diff-unified')).toBeTruthy()
    expect(screen.getByRole('region', { name: /unified yaml diff/i })).toBeTruthy()
  })

  it('shows gitops empty diff alert', () => {
    render(<GitOpsDiffViewer diffPreview="   " mode="unified" />)
    expect(screen.getByRole('alert')).toBeTruthy()
  })

  it('renders conflict modal actions', () => {
    render(
      <NoteConflictResolutionDialog
        open
        onClose={vi.fn()}
        localSnapshot={{ title: 'Local', contentBlocks: [], serialized: '{"title":"Local","contentBlocks":[]}' }}
        serverTitle="Server"
        serverPreview="server"
        localPreview="local"
        canSaveCopy
        mergeAnalysis={null}
        remoteLoading={false}
        onReloadLatest={vi.fn()}
        onSaveCopy={vi.fn()}
        onOverwrite={vi.fn()}
      />,
    )
    expect(screen.getByTestId('conflict-dialog')).toBeTruthy()
    expect(screen.getByTestId('conflict-reload-latest')).toBeTruthy()
  })

})
