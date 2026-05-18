type SearchFilterPanelProps = {
  sort: string
  onSortChange: (value: string) => void
  notebookId: string
  onNotebookIdChange: (value: string) => void
}

export function SearchFilterPanel({ sort, onSortChange, notebookId, onNotebookIdChange }: SearchFilterPanelProps) {
  return (
    <aside className="space-y-4 rounded-xl border border-outline-variant bg-surface-container-low p-4" aria-label="Search filters">
      <div>
        <label htmlFor="search-sort" className="text-label-md font-medium text-on-surface-variant">
          Sort
        </label>
        <select
          id="search-sort"
          className="mt-1 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-body-md"
          value={sort}
          onChange={(e) => onSortChange(e.target.value)}
        >
          <option value="relevance">Relevance</option>
          <option value="updatedAt,desc">Recently updated</option>
        </select>
      </div>
      <div>
        <label htmlFor="search-notebook" className="text-label-md font-medium text-on-surface-variant">
          Notebook ID
        </label>
        <input
          id="search-notebook"
          className="mt-1 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-2 text-body-md"
          placeholder="Optional filter"
          value={notebookId}
          onChange={(e) => onNotebookIdChange(e.target.value)}
        />
        <p className="mt-1 text-label-md text-on-surface-variant">Server-side notebook filter when supported.</p>
      </div>
    </aside>
  )
}
