import { type PropsWithChildren, useId } from 'react'
import { createPortal } from 'react-dom'

type Props = PropsWithChildren<{ open: boolean; title: string; onClose: () => void }>

export function Modal({ open, title, onClose, children }: Props) {
  const titleId = useId()
  if (!open) return null
  return createPortal(
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/40 p-4">
      <div
        className="max-h-[min(90vh,32rem)] w-full max-w-md overflow-y-auto rounded-lg border border-slate-200 bg-white p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
      >
        <div className="mb-3 flex items-center justify-between">
          <h3 id={titleId} className="text-sm font-semibold text-slate-900">
            {title}
          </h3>
          <button onClick={onClose} className="text-sm text-slate-500">
            Close
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}

