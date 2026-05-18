import { Outlet } from 'react-router-dom'
import { ResponsiveContent } from '../../shared/components/ResponsiveContent'
import { SettingsNav } from './SettingsNav'

export function SettingsLayout() {
  return (
    <ResponsiveContent>
      <div className="lg:flex lg:gap-8">
        <SettingsNav />
        <div className="min-w-0 flex-1 pt-6 lg:pt-0">
          <Outlet />
        </div>
      </div>
    </ResponsiveContent>
  )
}
