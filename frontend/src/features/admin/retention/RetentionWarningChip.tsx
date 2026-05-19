const WARNING_TONE: Record<string, string> = {
  CONTENT_RETENTION_LEGAL_HOLD_BLOCKED: 'bg-rose-50 text-rose-800 ring-rose-200',
  CONTENT_RETENTION_QUERY_CAPPED: 'bg-amber-50 text-amber-800 ring-amber-200',
  CONTENT_RETENTION_SERVICE_UNAVAILABLE: 'bg-rose-50 text-rose-800 ring-rose-200',
}

export function RetentionWarningChip({ code }: { code: string }) {
  const style = WARNING_TONE[code] ?? 'bg-amber-50 text-amber-800 ring-amber-200'
  return (
    <span
      className={`inline-flex rounded px-2 py-0.5 text-[10px] font-medium ring-1 ${style}`}
      data-testid="retention-warning-chip"
    >
      {code}
    </span>
  )
}
