import { Link } from 'react-router-dom'

export default function Brand({ portal }: { portal?: string }) {
  return (
    <Link to="/" className="brand">
      <span className="brand-mark" aria-hidden="true" />
      <span>Fertility Clinic</span>
      {portal && <span className="portal-tag">{portal}</span>}
      <span className="demo-tag">Demo · synthetic data</span>
    </Link>
  )
}
