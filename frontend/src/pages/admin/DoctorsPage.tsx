import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { clinicApi } from '../../api/clinicApi'
import { errorMessage } from '../../api/client'
import type { Doctor } from '../../types'
import { specialtyLabel } from '../../format'
import ErrorMessage from '../../components/ErrorMessage'

export default function DoctorsPage() {
  const [doctors, setDoctors] = useState<Doctor[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    clinicApi
      .listDoctors()
      .then(setDoctors)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Doctors</h1>
          <p className="muted">{doctors.length} registered · patients can be booked with any of them</p>
        </div>
        <Link className="btn btn-primary" to="/admin/doctors/new">
          Register doctor
        </Link>
      </div>
      <ErrorMessage message={error} />
      <div className="card">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : doctors.length === 0 ? (
          <p className="muted">No doctors registered yet.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Specialty</th>
                  <th>Email</th>
                  <th>Phone</th>
                </tr>
              </thead>
              <tbody>
                {doctors.map((d) => (
                  <tr key={d.id} data-testid={`doctor-row-${d.id}`}>
                    <td>{d.name}</td>
                    <td>
                      <span className="chip">{specialtyLabel(d.specialty)}</span>
                    </td>
                    <td>{d.email}</td>
                    <td>{d.phone || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
