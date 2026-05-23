/** Empty-state skeleton only — no mock notebook titles or avatars. */
export function RecentlyViewedPlaceholder() {
  return (
    <section aria-labelledby="recently-viewed-empty-title">
      <div className="mb-4 flex items-center justify-between">
        <h2 id="recently-viewed-empty-title" className="font-display text-headline-sm text-on-surface">
          Recently viewed
        </h2>
        <span className="text-label-md text-on-surface-variant">Nothing yet</span>
      </div>
      <ul className="grid grid-cols-1 gap-4 md:grid-cols-3" aria-label="Placeholder for recent items">
        {[1, 0.7, 0.4].map((opacity) => (
          <li
            key={opacity}
            className="flex flex-col gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4"
            style={{ opacity }}
          >
            <div className="flex items-start justify-between">
              <div className="h-12 w-12 animate-pulse rounded-lg bg-surface-container-high" />
              <div className="h-6 w-6 animate-pulse rounded-full bg-surface-container-high" />
            </div>
            <div className="space-y-2">
              <div className="h-4 w-3/4 animate-pulse rounded bg-surface-container-high" />
              <div className="h-3 w-1/2 animate-pulse rounded bg-surface-container-high" />
            </div>
            <div className="mt-auto flex items-center gap-2 border-t border-outline-variant pt-4">
              <div className="h-6 w-6 animate-pulse rounded-full bg-surface-container-high" />
              <div className="h-3 w-1/4 animate-pulse rounded bg-surface-container-high" />
            </div>
          </li>
        ))}
      </ul>
      <p className="mt-3 text-body-md text-on-surface-variant">
        Recent notebooks and notes appear here after you create content in a workspace.
      </p>
    </section>
  )
}
