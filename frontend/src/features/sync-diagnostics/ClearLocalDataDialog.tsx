import { ConfirmActionModal } from '../../shared/components/ConfirmActionModal'

type ClearLocalDataDialogProps = {
  open: boolean
  loading?: boolean
  onConfirm: () => void
  onClose: () => void
}

export function ClearLocalDataDialog({ open, loading, onConfirm, onClose }: ClearLocalDataDialogProps) {
  return (
    <ConfirmActionModal
      open={open}
      title="Clear local offline data?"
      message="Removes cached notes and offline drafts from this browser. You will not lose server data."
      confirmLabel="Clear local data"
      tone="danger"
      loading={loading}
      onConfirm={onConfirm}
      onClose={onClose}
    />
  )
}
