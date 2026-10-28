import { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import type { Role } from '../types'
import { useAuth } from './AuthContext'

/** Shows the page only to a signed-in user of the given portal; everyone else goes to that portal's login. */
export default function RequireRole({ role, loginPath, children }: { role: Role; loginPath: string; children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user || user.role !== role) {
    return <Navigate to={loginPath} replace state={{ from: location.pathname + location.search }} />
  }
  return <>{children}</>
}
