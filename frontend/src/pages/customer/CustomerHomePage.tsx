import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { customerApi } from '../../api/customerApi'
import { errorMessage } from '../../api/client'
import type { Appointment, Patient } from '../../types'
import { formatDate, interestLabel } from '../../format'
import AppointmentTable from '../../components/AppointmentTable'
import ErrorMessage from '../../components/ErrorMessage'

export default function CustomerHomePage() {
  const [profile, setProfile] = useState<Patient | null>(null)
  const [appointments, setAppointments] = useState<Appointment[]>([])
  const [error, setError] = useState<string | null>(null)

  const loadAppointments = () =>
    customerApi
      .listAppointments()
      .then(setAppointments)
      .catch((err) => setError(errorMessage(err)))

  useEffect(() => {
    customerApi
      .getProfile()
      .then(setProfile)
      .catch((err) => setError(errorMessage(err)))
    loadAppointments()
  }, [])

  const cancel = (id: number) => {
    if (!window.confirm('Cancel this visit?')) return
    setError(null)
    customerApi
      .cancelAppointment(id)
      .then(loadAppointments)
      .catch((err) => setError(errorMessage(err)))
  }

  return (
    <div>
      <ErrorMessage message={error} />
      {profile && (
        <>
          <div className="page-header">
            <div>
              <h1 data-testid="customer-name">Hello, {profile.firstName}</h1>
              <p className="muted">Your visits and details</p>
            </div>
            <Link className="btn btn-primary" to="/customer/book">
              Book a visit
            </Link>
          </div>
          <div className="card">
            <h3>My visits</h3>
            <AppointmentTable
              appointments={appointments}
              showPatient={false}
              onCancel={cancel}
              emptyText="You have no visits yet."
            />
          </div>
          <div className="card">
            <h3>My details</h3>
            <dl className="details">
              <div>
                <dt>Name</dt>
                <dd>
                  {profile.firstName} {profile.lastName}
                </dd>
              </div>
              <div>
                <dt>Date of birth</dt>
                <dd>{formatDate(profile.dateOfBirth)}</dd>
              </div>
              <div>
                <dt>Treatment interest</dt>
                <dd>
                  <span className="chip">{interestLabel(profile.treatmentInterest)}</span>
                </dd>
              </div>
              <div>
                <dt>Email</dt>
                <dd>{profile.email}</dd>
              </div>
              <div>
                <dt>Phone</dt>
                <dd>{profile.phone || '—'}</dd>
              </div>
            </dl>
            <p className="muted">To change your details, please contact the clinic.</p>
          </div>
        </>
      )}
    </div>
  )
}
