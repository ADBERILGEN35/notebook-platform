import { type PropsWithChildren, useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'

type Props = PropsWithChildren<{ open: boolean; title: string; onClose: () => void }>

export function Modal({ open, title, onClose, children }: Props) {
  const titleId = useId()
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [open, onClose])

  if (!open) return null
  return createPortal(
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/40 p-4" role="presentation">
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
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            className="rounded px-2 py-1 text-sm text-slate-500 hover:bg-slate-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary-500"
            aria-label="Close dialog"
          >
            Close
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}
