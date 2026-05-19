import { Card } from '../../../shared/components/Card'

export type PurgeConfirmationDialogProps = {
  open: boolean
  reason: string
  confirmText: string
  busy: boolean
  purgeEnabled: boolean
  mfaRequired?: boolean
  dryRunReference?: string
  onReasonChange: (v: string) => void
  onConfirmTextChange: (v: string) => void
  onConfirm: () => void
  onCancel: () => void
}

export function PurgeConfirmationDialog({
  open,
  reason,
  confirmText,
  busy,
  purgeEnabled,
  mfaRequired,
  dryRunReference,
  onReasonChange,
  onConfirmTextChange,
  onConfirm,
  onCancel,
}: PurgeConfirmationDialogProps) {
  if (!open) return null

  const reasonOk = reason.trim().length >= 10
  const confirmOk = confirmText.trim() === 'DELETE'
  const canExecute = purgeEnabled && reasonOk && confirmOk && !busy

  return (
    <Card
      className="space-y-3 border-red-200 p-4"
      role="dialog"
      aria-labelledby="purge-confirm-title"
      data-testid="purge-confirmation-dialog"
    >
      <p id="purge-confirm-title" className="text-sm font-semibold text-red-900">
        Destructive purge confirmation
      </p>
      <p className="text-sm text-slate-700">
        This permanently deletes eligible aggregate operational rows up to the server batch limit. Production purge UI
        remains disabled unless explicitly enabled by feature flag and server policy.
      </p>
      {mfaRequired ? (
        <p className="rounded border border-amber-200 bg-amber-50 p-2 text-xs text-amber-900">
          MFA verification is required before the server accepts a destructive purge.
        </p>
      ) : null}
      {dryRunReference ? (
        <p className="text-xs text-slate-600">
          Dry-run reference: <span className="font-mono">{dryRunReference}</span>
        </p>
      ) : null}
      {!purgeEnabled ? (
        <p className="rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-700">
          Destructive purge is disabled in this environment (feature flag off). Review dry-run plans only.
        </p>
      ) : null}
      <label className="block text-xs font-medium text-slate-600">
        Reason (required, min 10 chars)
        <textarea
          className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
          rows={3}
          value={reason}
          onChange={(e) => onReasonChange(e.target.value)}
        />
      </label>
      <label className="block text-xs font-medium text-slate-600">
        Type DELETE to confirm
        <input
          className="mt-1 w-full rounded border border-slate-300 p-2 text-sm"
          value={confirmText}
          onChange={(e) => onConfirmTextChange(e.target.value)}
        />
      </label>
      <div className="flex gap-2">
        <button
          type="button"
          className="rounded bg-red-700 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
          disabled={!canExecute}
          onClick={onConfirm}
        >
          Execute purge
        </button>
        <button type="button" className="rounded border border-slate-300 px-3 py-1.5 text-sm" disabled={busy} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </Card>
  )
}
