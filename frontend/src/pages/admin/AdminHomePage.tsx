import { Link } from 'react-router-dom'
import { PageHeader } from '../../shared/components/PageHeader'
import { Card } from '../../shared/components/Card'

export function AdminHomePage() {
  return (
    <div className="space-y-4">
      <PageHeader
        title="Admin"
        subtitle="Operational visibility for platform teams. Audit data is mock-backed until the Faz 43 admin proxy lands."
      />

      <Card>
        <h2 className="text-sm font-semibold text-slate-800">Audit</h2>
        <p className="mt-1 text-sm text-slate-600">
          Browse audit events with filters, pagination, and safe metadata display.
        </p>
        <Link
          className="mt-3 inline-block text-sm font-medium text-primary-600 hover:underline"
          to="/app/admin/audit"
        >
          Open audit events
        </Link>
      </Card>
    </div>
  )
}
