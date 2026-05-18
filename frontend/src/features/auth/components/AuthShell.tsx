import type { PropsWithChildren } from 'react'

export function AuthShell({ children }: PropsWithChildren) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center bg-surface-container-low p-gutter font-body text-body-md text-on-surface">
      <div className="pointer-events-none fixed inset-0 -z-10 overflow-hidden" aria-hidden>
        <span className="absolute left-[-10%] top-[-10%] block h-[40%] w-[40%] rounded-full bg-primary/5 blur-[120px]" />
        <div className="absolute bottom-[-10%] right-[-10%] h-[40%] w-[40%] rounded-full bg-secondary-container/30 blur-[120px]" />
      </div>
      <div className="w-full max-w-[480px]">{children}</div>
    </div>
  )
}
