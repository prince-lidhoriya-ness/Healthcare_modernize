import { FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { Role } from '../types'
import Brand from '../components/Brand'
import ErrorMessage from '../components/ErrorMessage'

const PORTALS: Record<Role, { name: string; home: string; usernameLabel: string; other: string; otherPath: string }> = {
  ADMIN: {
    name: 'Admin portal',
    home: '/admin',
    usernameLabel: 'Username',
    other: 'Are you a patient? Go to the customer portal',
    otherPath: '/customer/login',
  },
  CUSTOMER: {
    name: 'Customer portal',
    home: '/customer',
    usernameLabel: 'Email',
    other: 'Clinic staff? Go to the admin portal',
    otherPath: '/admin/login',
  },
}

export default function LoginPage({ portal }: { portal: Role }) {
  const config = PORTALS[portal]
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    login(username, password, portal)
      .then(() => navigate(from && from.startsWith(config.home) ? from : config.home, { replace: true }))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setBusy(false))
  }

  return (
    <div className="auth-screen">
      <Brand portal={config.name} />
      <form className="card form auth-card" onSubmit={onSubmit}>
        <div>
          <h1>Sign in</h1>
          <p className="muted">{config.name}</p>
        </div>
        <ErrorMessage message={error} />
        <label>
          {config.usernameLabel}
          <input
            required
            autoFocus
            autoComplete="username"
            type={portal === 'CUSTOMER' ? 'email' : 'text'}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </label>
        <label>
          Password
          <input
            required
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        {portal === 'CUSTOMER' && (
          <p className="muted">
            New patient? <Link to="/customer/register">Create an account</Link>
          </p>
        )}
        <p className="muted">
          <Link to={config.otherPath}>{config.other}</Link>
        </p>
      </form>
    </div>
  )
}
