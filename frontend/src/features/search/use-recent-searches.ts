const STORAGE_KEY = 'np_recent_searches'
const MAX = 8

export function readRecentSearches(): string[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as unknown
    return Array.isArray(parsed) ? parsed.filter((q) => typeof q === 'string').slice(0, MAX) : []
  } catch {
    return []
  }
}

export function pushRecentSearch(query: string) {
  const trimmed = query.trim()
  if (trimmed.length < 2) return
  const next = [trimmed, ...readRecentSearches().filter((q) => q !== trimmed)].slice(0, MAX)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
}
