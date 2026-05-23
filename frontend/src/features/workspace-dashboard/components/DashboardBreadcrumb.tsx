export function DashboardBreadcrumb() {
  return (
    <nav aria-label="Breadcrumb" className="mb-6">
      <ol className="flex items-center gap-1 text-label-md text-on-surface-variant">
        <li>
          <span className="sr-only">Home</span>
          <span aria-hidden className="text-on-surface-variant">
            ⌂
          </span>
        </li>
        <li aria-hidden>/</li>
        <li>
          <span className="text-on-surface" aria-current="page">
            Dashboard
          </span>
        </li>
      </ol>
    </nav>
  )
}
