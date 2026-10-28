import { statusLabel } from '../format'

interface Props {
  title: string
  data: Record<string, number>
  statusKeys?: boolean
}

export default function BarList({ title, data, statusKeys }: Props) {
  const entries = Object.entries(data)
  const max = Math.max(1, ...entries.map(([, v]) => v))
  return (
    <div className="card">
      <h3>{title}</h3>
      <ul className="bar-list">
        {entries.map(([key, value]) => (
          <li key={key}>
            <span className="bar-label">{statusKeys ? statusLabel(key) : key}</span>
            <span className="bar-track">
              <span className="bar-fill" style={{ width: `${(value / max) * 100}%` }} />
            </span>
            <span className="bar-value">{value}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
