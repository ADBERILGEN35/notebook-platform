import { beforeEach, describe, expect, it } from 'vitest'
import { useWorkspaceStore } from './workspace-store'

describe('workspace store', () => {
  beforeEach(() => {
    localStorage.clear()
    useWorkspaceStore.getState().setActiveWorkspaceId(null)
  })

  it('updates active workspace state', () => {
    useWorkspaceStore.getState().setActiveWorkspaceId('workspace-1')
    expect(useWorkspaceStore.getState().activeWorkspaceId).toBe('workspace-1')
  })
})

