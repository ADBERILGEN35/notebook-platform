const STORAGE_KEY = 'notebook_onboarding_v1_completed'

export function isOnboardingComplete(): boolean {
  if (typeof window === 'undefined') return true
  return window.localStorage.getItem(STORAGE_KEY) === 'true'
}

export function markOnboardingComplete(): void {
  window.localStorage.setItem(STORAGE_KEY, 'true')
}

/** Test-only reset */
export function resetOnboardingForTests(): void {
  window.localStorage.removeItem(STORAGE_KEY)
}

export const ONBOARDING_STEPS = [
  'welcome',
  'workspace',
  'notebook',
  'first-note',
] as const

export type OnboardingStepId = (typeof ONBOARDING_STEPS)[number]
