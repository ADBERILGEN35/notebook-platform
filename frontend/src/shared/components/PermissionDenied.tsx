export function PermissionDenied({
  title = 'Permission denied',
  message = 'You do not have permission to access this resource.',
}: {
  title?: string
  message?: string
}) {
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
      <p className="font-semibold">{title}</p>
      <p className="mt-2">{message}</p>
    </div>
  )
}
