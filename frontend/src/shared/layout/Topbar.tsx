import { Link } from 'react-router-dom'
import { Input } from '../components/Input'
import { Button } from '../components/Button'
import { NotificationBell } from '../../features/notifications/components/NotificationBell'

type Props = {
  search: string
  onSearchChange: (value: string) => void
  onCreateNote: () => void
}

export function Topbar({ search, onSearchChange, onCreateNote }: Props) {
  return (
    <header className="flex items-center gap-3 border-b border-slate-200 bg-white px-4 py-3">
      <Input
        placeholder="Search notes..."
        value={search}
        onChange={(event) => onSearchChange(event.target.value)}
      />
      <Button className="bg-primary-600 text-white hover:bg-primary-700" onClick={onCreateNote}>
        Create note
      </Button>
      <NotificationBell />
      <Link to="/app/settings/security" className="text-sm text-slate-600">
        Security
      </Link>
    </header>
  )
}

