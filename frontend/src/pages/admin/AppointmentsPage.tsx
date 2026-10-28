import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { clinicApi } from '../api/clinicApi'
import { errorMessage } from '../api/client'
import type { Appointment, AppointmentStatus } from '../types'
import AppointmentTable from '../components/AppointmentTable'
import ErrorMessage from '../components/ErrorMessage'

export default function AppointmentsPage() {
  const [appointments, setAppointments] = useState<Appointment[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = () =>
    clinicApi
      .listUpcomingAppointments()
      .then(setAppointments)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))

  useEffect(() => {
    load()
  }, [])

  const changeStatus = (id: number, status: AppointmentStatus) => {
    setError(null)
    clinicApi
      .updateAppointmentStatus(id, status)
      .then(load)
      .catch((err) => setError(errorMessage(err)))
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Upcoming appointments</h1>
          <p className="muted">Scheduled visits from now on</p>
        </div>
        <Link className="btn btn-primary" to="/appointments/new">
          Book visit
        </Link>
      </div>
      <ErrorMessage message={error} />
      <div className="card">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : (
          <AppointmentTable
            appointments={appointments}
            onStatusChange={changeStatus}
            emptyText="No upcoming appointments."
          />
        )}
      </div>
    </div>
  )
}
