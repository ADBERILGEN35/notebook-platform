const STORAGE_KEY = 'np_saved_searches'

export type SavedSearch = {
  id: string
  label: string
  query: string
  createdAt: string
}

export function readSavedSearches(): SavedSearch[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? (parsed as SavedSearch[]) : []
  } catch {
    return []
  }
}

export function saveSearch(label: string, query: string): SavedSearch[] {
  const item: SavedSearch = {
    id: crypto.randomUUID(),
    label: label.trim() || query.trim(),
    query: query.trim(),
    createdAt: new Date().toISOString(),
  }
  const next = [item, ...readSavedSearches()].slice(0, 20)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  return next
}

export function removeSavedSearch(id: string): SavedSearch[] {
  const next = readSavedSearches().filter((s) => s.id !== id)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  return next
}
