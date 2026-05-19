/** Mask opaque session identifiers for admin UI (never show full UUID/JTI). */
export function maskSessionId(id: string | null | undefined): string {
  const v = (id ?? '').trim()
  if (!v) return '—'
  if (v.length <= 12) return `${v.slice(0, 2)}…${v.slice(-2)}`
  return `${v.slice(0, 4)}…${v.slice(-4)}`
}
