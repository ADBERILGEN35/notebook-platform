/** User-safe error text — never surfaces tokens or raw auth headers. */
export function sanitizeErrorMessage(error: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (!error) return fallback
  if (typeof error === 'string') return stripSensitive(error) || fallback
  if (error instanceof Error) return stripSensitive(error.message) || fallback
  return fallback
}

function stripSensitive(text: string): string {
  return text
    .replace(/eyJ[A-Za-z0-9_-]{10,}/g, '[redacted]')
    .replace(/Bearer\s+\S+/gi, '[redacted]')
    .trim()
}
