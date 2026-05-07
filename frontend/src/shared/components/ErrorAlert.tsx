import { readableErrorMessage } from '../api/api-client'

export function ErrorAlert({ error }: { error: unknown }) {
  return (
    <div className="rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
      {readableErrorMessage(error)}
    </div>
  )
}

