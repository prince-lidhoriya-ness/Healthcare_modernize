import { Link } from 'react-router-dom'
import Brand from '../components/Brand'

export default function PortalChooserPage() {
  return (
    <div className="auth-screen">
      <Brand />
      <h1>Welcome</h1>
      <p className="muted">Choose how you want to sign in.</p>
      <div className="portal-grid">
        <Link to="/customer" className="card portal-card">
          <h3>Customer portal</h3>
          <p className="muted">For patients: see your visits, book a new one or cancel.</p>
          <span className="btn btn-primary">Customer sign in</span>
        </Link>
        <Link to="/admin" className="card portal-card">
          <h3>Admin portal</h3>
          <p className="muted">For clinic staff: patients, schedule and dashboard.</p>
          <span className="btn">Admin sign in</span>
        </Link>
      </div>
    </div>
  )
}
