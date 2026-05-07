const SENSITIVE_KEY_PATTERN =
  /password|token|secret|authorization|cookie|private|apikey|access_key/i

export function maskSensitiveKeysInObject(raw: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(raw)) {
    out[k] = SENSITIVE_KEY_PATTERN.test(k) ? '***masked***' : maskSensitiveMetadata(v)
  }
  return out
}

/** Second-line masking for audit metadata before UI render; never interpolate as HTML. */
export function maskSensitiveMetadata(value: unknown): unknown {
  if (value === null || value === undefined) return value
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value
  }
  if (Array.isArray(value)) {
    return value.map((entry) => maskSensitiveMetadata(entry))
  }
  if (typeof value === 'object') {
    return maskSensitiveKeysInObject(value as Record<string, unknown>)
  }
  return String(value)
}
