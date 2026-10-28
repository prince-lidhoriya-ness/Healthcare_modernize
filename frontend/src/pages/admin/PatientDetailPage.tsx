import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { clinicApi } from '../../api/clinicApi'
import { errorMessage } from '../../api/client'
import type { Appointment, AppointmentStatus, Patient } from '../../types'
import { formatDate, interestLabel } from '../../format'
import AppointmentTable from '../../components/AppointmentTable'
import ErrorMessage from '../../components/ErrorMessage'

export default function PatientDetailPage() {
  const { id } = useParams()
  const patientId = Number(id)
  const [patient, setPatient] = useState<Patient | null>(null)
  const [appointments, setAppointments] = useState<Appointment[]>([])
  const [error, setError] = useState<string | null>(null)

  const loadAppointments = () =>
    clinicApi
      .listPatientAppointments(patientId)
      .then(setAppointments)
      .catch((err) => setError(errorMessage(err)))

  useEffect(() => {
    clinicApi
      .getPatient(patientId)
      .then(setPatient)
      .catch((err) => setError(errorMessage(err)))
    loadAppointments()
  }, [patientId])

  const changeStatus = (appointmentId: number, status: AppointmentStatus) => {
    setError(null)
    clinicApi
      .updateAppointmentStatus(appointmentId, status)
      .then(loadAppointments)
      .catch((err) => setError(errorMessage(err)))
  }

  return (
    <div>
      <ErrorMessage message={error} />
      {patient && (
        <>
          <div className="page-header">
            <div>
              <h1 data-testid="patient-name">
                {patient.firstName} {patient.lastName}
              </h1>
              <p className="muted">Patient #{patient.id}</p>
            </div>
            <div className="header-actions">
              <Link className="btn" to={`/admin/patients/${patient.id}/edit`}>
                Edit
              </Link>
              <Link className="btn btn-primary" to={`/admin/appointments/new?patientId=${patient.id}`}>
                Book visit
              </Link>
            </div>
          </div>
          <div className="card">
            <dl className="details">
              <div>
                <dt>Date of birth</dt>
                <dd>{formatDate(patient.dateOfBirth)}</dd>
              </div>
              <div>
                <dt>Treatment interest</dt>
                <dd>
                  <span className="chip">{interestLabel(patient.treatmentInterest)}</span>
                </dd>
              </div>
              <div>
                <dt>Email</dt>
                <dd>{patient.email}</dd>
              </div>
              <div>
                <dt>Phone</dt>
                <dd>{patient.phone || '—'}</dd>
              </div>
            </dl>
          </div>
          <div className="card">
            <h3>Appointments</h3>
            <AppointmentTable
              appointments={appointments}
              showPatient={false}
              onStatusChange={changeStatus}
              emptyText="No appointments yet."
            />
          </div>
        </>
      )}
    </div>
  )
}
