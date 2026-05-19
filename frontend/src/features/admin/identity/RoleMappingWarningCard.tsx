import { Card } from '../../../shared/components/Card'

export function RoleMappingWarningCard({
  title,
  message,
}: {
  title: string
  message: string
}) {
  return (
    <Card className="border-amber-200 bg-amber-50">
      <p className="text-sm font-semibold text-amber-950">{title}</p>
      <p className="mt-1 text-sm text-amber-900">{message}</p>
    </Card>
  )
}
