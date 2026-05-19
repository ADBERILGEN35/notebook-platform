import { useState } from 'react'
import { AdminPageShell } from '../../features/admin/shared/AdminPageShell'
import { AdminSearchPanel } from '../../features/admin/search/AdminSearchPanel'
import { AdminRunbookLink } from '../../features/admin/shared/AdminRunbookLink'

export function AdminSearchDiagnosticsPage() {
  const [query, setQuery] = useState('')

  return (
    <AdminPageShell
      title="Admin search & diagnostics"
      subtitle="Navigate diagnostic areas. For audit event search, open Audit Events and use filters."
    >
      <AdminSearchPanel query={query} onQueryChange={setQuery} />
      <AdminRunbookLink docPath="docs/admin-audit-proxy.md" label="Audit diagnostics" />
    </AdminPageShell>
  )
}
