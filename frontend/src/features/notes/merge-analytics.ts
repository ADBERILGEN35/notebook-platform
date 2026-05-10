export type MergeAction =
  | 'apply_merge'
  | 'reload_latest'
  | 'save_copy'
  | 'overwrite_latest'
  | 'cancel'
  | 'dialog_opened'
  | 'backend_analyze_fallback'

export type MergeEvent = {
  source: 'online' | 'offline_draft'
  backendAnalyzeUsed: boolean
  backendApplyUsed: boolean
  action: MergeAction
  hasSafeSuggestion: boolean
  conflictCount: number
}

export type MergeAnalyticsAdapter = {
  track: (event: MergeEvent) => void
}

const noopAdapter: MergeAnalyticsAdapter = {
  track: () => {},
}

let adapter: MergeAnalyticsAdapter = noopAdapter

export function setMergeAnalyticsAdapter(next: MergeAnalyticsAdapter) {
  adapter = next
}

export function trackMergeEvent(event: MergeEvent) {
  adapter.track(event)
}
