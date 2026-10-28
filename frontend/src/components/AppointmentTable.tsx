import { Link } from 'react-router-dom'
import type { Appointment, AppointmentStatus } from '../types'
import { formatDateTime } from '../format'
import StatusBadge from './StatusBadge'

interface Props {
  appointments: Appointment[]
  showPatient?: boolean
  onStatusChange?: (id: number, status: AppointmentStatus) => void
  emptyText?: string
}

export default function AppointmentTable({ appointments, showPatient = true, onStatusChange, emptyText }: Props) {
  if (appointments.length === 0) {
    return <p className="muted">{emptyText || 'No appointments.'}</p>
  }
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>When</th>
            {showPatient && <th>Patient</th>}
            <th>Visit type</th>
            <th>Clinician</th>
            <th>Status</th>
            {onStatusChange && <th></th>}
          </tr>
        </thead>
        <tbody>
          {appointments.map((a) => (
            <tr key={a.id} data-testid={`appointment-row-${a.id}`}>
              <td className="nowrap">{formatDateTime(a.startTime)}</td>
              {showPatient && (
                <td>
                  <Link to={`/patients/${a.patientId}`}>{a.patientName}</Link>
                </td>
              )}
              <td>
                {a.visitTypeLabel} <span className="muted">· {a.durationMinutes} min</span>
              </td>
              <td>{a.clinician}</td>
              <td>
                <StatusBadge status={a.status} />
              </td>
              {onStatusChange && (
                <td>
                  {a.status === 'SCHEDULED' && (
                    <div className="actions">
                      <button className="btn btn-small" onClick={() => onStatusChange(a.id, 'COMPLETED')}>
                        Complete
                      </button>
                      <button className="btn btn-small btn-ghost" onClick={() => onStatusChange(a.id, 'NO_SHOW')}>
                        No-show
                      </button>
                      <button className="btn btn-small btn-danger" onClick={() => onStatusChange(a.id, 'CANCELLED')}>
                        Cancel
                      </button>
                    </div>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
