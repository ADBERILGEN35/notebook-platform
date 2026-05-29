import { Link } from 'react-router-dom'
import type { Notebook } from '../../../shared/types/api'
import { formatRelativeTime } from '../utils/format-relative-time'

type RecentlyViewedSectionProps = {
  notebooks: Notebook[]
  focusWorkspaceId: string | null
  isLoading: boolean
  viewAllHref?: string
}

/**
 * "Recently Viewed" section. Production rule: never invent fake content —
 * when there is nothing to show, render the design's tiered-opacity skeleton.
 */
export function RecentlyViewedSection({
  notebooks,
  focusWorkspaceId,
  isLoading,
  viewAllHref,
}: RecentlyViewedSectionProps) {
  const showSkeleton = isLoading || notebooks.length === 0

  return (
    <section aria-labelledby="recently-viewed-title">
      <header className="mb-4 flex items-center justify-between">
        <h2
          id="recently-viewed-title"
          className="font-display text-headline-sm text-on-surface"
        >
          Recently Viewed
        </h2>
        {focusWorkspaceId && viewAllHref ? (
          <Link
            to={viewAllHref}
            className="text-label-md font-medium text-primary hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
          >
            View All
          </Link>
        ) : (
          <span className="text-label-md text-on-surface-variant" aria-live="polite">
            {isLoading ? 'Loading…' : 'Nothing yet'}
          </span>
        )}
      </header>

      {showSkeleton ? (
        <ul
          className="grid grid-cols-1 gap-4 md:grid-cols-3"
          aria-label="Recently viewed placeholder"
        >
          {[1, 0.7, 0.4].map((opacity) => (
            <li
              key={opacity}
              className="flex flex-col gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4"
              style={{ opacity }}
              aria-hidden
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
      ) : (
        <ul
          className="grid grid-cols-1 gap-4 md:grid-cols-3"
          data-testid="recently-viewed-list"
        >
          {notebooks.slice(0, 3).map((notebook) => (
            <li key={notebook.id}>
              <Link
                to={`/app/notebooks/${notebook.id}`}
                className="group flex h-full flex-col gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 transition-all hover:border-primary/30 hover:shadow-auth-card focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
              >
                <div className="flex items-start justify-between">
                  <span className="flex h-12 w-12 items-center justify-center rounded-lg bg-primary-fixed text-headline-sm text-primary">
                    <span aria-hidden>📓</span>
                  </span>
                </div>
                <div className="space-y-1">
                  <p className="line-clamp-1 font-medium text-on-surface group-hover:text-primary">
                    {notebook.name}
                  </p>
                  <p className="text-label-md text-on-surface-variant">
                    Updated {formatRelativeTime(notebook.updatedAt)}
                  </p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
