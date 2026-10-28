import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { clinicApi } from '../api/clinicApi'
import { errorMessage } from '../api/client'
import type { DashboardSummary } from '../types'
import StatCard from '../components/StatCard'
import BarList from '../components/BarList'
import AppointmentTable from '../components/AppointmentTable'
import ErrorMessage from '../components/ErrorMessage'

export default function DashboardPage() {
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    clinicApi
      .getDashboard()
      .then(setSummary)
      .catch((err) => setError(errorMessage(err)))
  }, [])

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p className="muted">Clinic overview{summary ? ` · updated ${summary.generatedAt}` : ''}</p>
        </div>
        <div className="header-actions">
          <Link className="btn" to="/patients/new">
            Register patient
          </Link>
          <Link className="btn btn-primary" to="/appointments/new">
            Book visit
          </Link>
        </div>
      </div>
      <ErrorMessage message={error} />
      {!summary && !error && <p className="muted">Loading…</p>}
      {summary && (
        <>
          <div className="stat-grid">
            <StatCard label="Registered patients" value={summary.totalPatients} testId="stat-total-patients" />
            <StatCard label="New this week" value={summary.newPatientsThisWeek} />
            <StatCard label="Visits today" value={summary.appointmentsToday} />
            <StatCard label="Visits next 7 days" value={summary.upcomingNext7Days} testId="stat-upcoming" />
          </div>
          <div className="card">
            <h3>Next 7 days</h3>
            <AppointmentTable
              appointments={summary.nextAppointments}
              emptyText="No visits scheduled in the next 7 days."
            />
          </div>
          <div className="two-col">
            <BarList title="Appointments by status" data={summary.appointmentsByStatus} statusKeys />
            <BarList title="Appointments by visit type" data={summary.appointmentsByVisitType} />
          </div>
        </>
      )}
    </div>
  )
}
