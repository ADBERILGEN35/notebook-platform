import { readableErrorMessage } from '../api/api-client'

export function ErrorAlert({ error, message }: { error?: unknown; message?: string }) {
  const text = message != null && message !== '' ? message : readableErrorMessage(error)
  return (
    <div className="rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
      {text}
    </div>
  )
}

