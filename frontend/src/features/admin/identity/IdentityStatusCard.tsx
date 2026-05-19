import { AdminHealthCard } from '../shared/AdminHealthCard'

export function IdentityStatusCard({
  title,
  statusLabel,
  ok,
  detail,
}: {
  title: string
  statusLabel: string
  ok: boolean
  detail?: string
}) {
  return <AdminHealthCard title={title} statusLabel={statusLabel} ok={ok} detail={detail} />
}
