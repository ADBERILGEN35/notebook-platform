import type { ReactNode } from 'react'
import { Modal } from './Modal'
import { Button } from './Button'

type ConfirmActionModalProps = {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  tone?: 'default' | 'danger'
  loading?: boolean
  onConfirm: () => void
  onClose: () => void
  children?: ReactNode
}

export function ConfirmActionModal({
  open,
  title,
  message,
  confirmLabel = 'Confirm',
  cancelLabel = 'Cancel',
  tone = 'default',
  loading,
  onConfirm,
  onClose,
  children,
}: ConfirmActionModalProps) {
  return (
    <Modal open={open} title={title} onClose={onClose}>
      <p className="text-body-md text-on-surface-variant">{message}</p>
      {children}
      <div className="mt-6 flex flex-wrap justify-end gap-2">
        <Button type="button" onClick={onClose} disabled={loading}>
          {cancelLabel}
        </Button>
        <Button
          type="button"
          className={tone === 'danger' ? 'bg-error text-white hover:opacity-90' : 'bg-primary text-white hover:bg-primary-container'}
          onClick={onConfirm}
          disabled={loading}
        >
          {confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
