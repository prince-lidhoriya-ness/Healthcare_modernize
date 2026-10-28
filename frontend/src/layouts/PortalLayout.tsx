import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import Brand from '../components/Brand'

interface Props {
  portalName: string
  links: { to: string; label: string; end?: boolean }[]
  loginPath: string
}

/** Top bar + page area shared by the admin and customer portals. */
export default function PortalLayout({ portalName, links, loginPath }: Props) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const signOut = () => {
    logout().finally(() => navigate(loginPath, { replace: true }))
  }

  return (
    <div className="app">
      <header className="topbar">
        <Brand portal={portalName} />
        <nav>
          {links.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end}>
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className="user-menu">
          <span className="muted" data-testid="signed-in-user">
            {user?.displayName}
          </span>
          <button className="btn btn-small btn-ghost" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
