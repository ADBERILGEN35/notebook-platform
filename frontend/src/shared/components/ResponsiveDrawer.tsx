import { useEffect, useRef, type PropsWithChildren } from 'react'

type Props = PropsWithChildren<{
  open: boolean
  onClose: () => void
  title?: string
  side?: 'left' | 'right'
  className?: string
  testId?: string
}>

export function ResponsiveDrawer({
  open,
  onClose,
  title,
  side = 'right',
  className = '',
  testId,
  children,
}: Props) {
  const closeButtonRef = useRef<HTMLButtonElement | null>(null)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    const body = document.body
    const previousOverflow = body.style.overflow
    body.style.overflow = 'hidden'
    closeButtonRef.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      body.style.overflow = previousOverflow
    }
  }, [open, onClose])

  if (!open) return null

  const positionClass = side === 'left' ? 'left-0 border-r' : 'right-0 border-l'
  return (
    <>
      <div className="fixed inset-0 z-40 bg-slate-900/40" onClick={onClose} aria-hidden />
      <aside
        data-testid={testId}
        className={`fixed inset-y-0 ${positionClass} z-50 w-full max-w-sm overflow-y-auto bg-white shadow-xl ${className}`}
        role="dialog"
        aria-modal="true"
        aria-label={title || 'Drawer'}
      >
        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
          <p className="text-sm font-semibold text-slate-900">{title || 'Panel'}</p>
          <button
            ref={closeButtonRef}
            type="button"
            className="rounded px-2 py-1 text-sm text-slate-600 hover:bg-slate-100 focus:outline-none focus:ring-2 focus:ring-primary-500"
            onClick={onClose}
            aria-label="Close drawer"
          >
            Close
          </button>
        </div>
        <div className={`p-3 ${className}`}>{children}</div>
      </aside>
    </>
  )
}
