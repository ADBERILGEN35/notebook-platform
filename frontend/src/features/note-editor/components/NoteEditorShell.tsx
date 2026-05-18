import type { PropsWithChildren, ReactNode } from 'react'

type NoteEditorShellProps = PropsWithChildren<{
  header: ReactNode
  sidePanel?: ReactNode
  sidePanelOpen?: boolean
}>

export function NoteEditorShell({ header, sidePanel, sidePanelOpen, children }: NoteEditorShellProps) {
  return (
    <div className="flex h-full min-h-[calc(100vh-8rem)] flex-col lg:flex-row lg:gap-4">
      <div className="flex min-w-0 flex-1 flex-col">
        {header}
        <div className="min-h-0 flex-1">{children}</div>
      </div>
      {sidePanel ? (
        <aside
          className={`w-full shrink-0 lg:w-80 xl:w-96 ${sidePanelOpen === false ? 'hidden lg:block' : ''}`}
          aria-label="Note side panel"
        >
          {sidePanel}
        </aside>
      ) : null}
    </div>
  )
}
