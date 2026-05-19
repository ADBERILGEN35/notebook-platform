import { Navigate } from 'react-router-dom'

/** Legacy index route redirects to Faz 142 overview. */
export function AdminHomePage() {
  return <Navigate to="/app/admin/overview" replace />
}
