import { FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { clinicApi } from '../api/clinicApi'
import { errorMessage } from '../api/client'
import type { Patient, ReferenceData, VisitType } from '../types'
import ErrorMessage from '../components/ErrorMessage'

// 15-minute slots during clinic hours (08:00 - 19:00)
function timeSlots(): string[] {
  const slots: string[] = []
  for (let h = 8; h < 19; h++) {
    for (let m = 0; m < 60; m += 15) {
      slots.push(`${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`)
    }
  }
  return slots
}

export default function AppointmentFormPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const [patients, setPatients] = useState<Patient[]>([])
  const [refData, setRefData] = useState<ReferenceData | null>(null)
  const [patientId, setPatientId] = useState<string>(params.get('patientId') || '')
  const [visitType, setVisitType] = useState<VisitType | ''>('')
  const [clinician, setClinician] = useState('')
  const [date, setDate] = useState('')
  const [time, setTime] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const slots = useMemo(timeSlots, [])

  useEffect(() => {
    clinicApi.listPatients().then(setPatients).catch((err) => setError(errorMessage(err)))
    clinicApi.getReferenceData().then(setRefData).catch((err) => setError(errorMessage(err)))
  }, [])

  const selectedType = refData?.visitTypes.find((v) => v.code === visitType)

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    clinicApi
      .createAppointment({
        patientId: Number(patientId),
        visitType,
        clinician,
        startTime: `${date}T${time}`,
      })
      .then((saved) => navigate(`/patients/${saved.patientId}`))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setSaving(false))
  }

  return (
    <div className="narrow">
      <div className="page-header">
        <div>
          <h1>Book a visit</h1>
          <p className="muted">Clinic hours Monday–Saturday, 08:00–19:00</p>
        </div>
      </div>
      <ErrorMessage message={error} />
      <form className="card form" onSubmit={onSubmit}>
        <label>
          Patient
          <select required value={patientId} onChange={(e) => setPatientId(e.target.value)}>
            <option value="">Select patient…</option>
            {patients.map((p) => (
              <option key={p.id} value={p.id}>
                {p.lastName}, {p.firstName} ({p.email})
              </option>
            ))}
          </select>
        </label>
        <div className="form-row">
          <label>
            Visit type
            <select required value={visitType} onChange={(e) => setVisitType(e.target.value as VisitType)}>
              <option value="">Select…</option>
              {refData?.visitTypes.map((v) => (
                <option key={v.code} value={v.code}>
                  {v.label} ({v.durationMinutes} min)
                </option>
              ))}
            </select>
          </label>
          <label>
            Clinician
            <select required value={clinician} onChange={(e) => setClinician(e.target.value)}>
              <option value="">Select…</option>
              {refData?.clinicians.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="form-row">
          <label>
            Date
            <input type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
          <label>
            Start time
            <select required value={time} onChange={(e) => setTime(e.target.value)}>
              <option value="">Select…</option>
              {slots.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
        </div>
        {selectedType && <p className="muted">This visit takes {selectedType.durationMinutes} minutes.</p>}
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={() => navigate(-1)}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Booking…' : 'Book visit'}
          </button>
        </div>
      </form>
    </div>
  )
}
