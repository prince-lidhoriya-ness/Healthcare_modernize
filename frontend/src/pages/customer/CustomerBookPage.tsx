import { FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { clinicApi } from '../../api/clinicApi'
import { customerApi } from '../../api/customerApi'
import { errorMessage } from '../../api/client'
import type { ReferenceData, VisitType } from '../../types'
import ErrorMessage from '../../components/ErrorMessage'
import { timeSlots } from '../../clinicHours'

export default function CustomerBookPage() {
  const navigate = useNavigate()
  const [refData, setRefData] = useState<ReferenceData | null>(null)
  const [visitType, setVisitType] = useState<VisitType | ''>('')
  const [clinician, setClinician] = useState('')
  const [date, setDate] = useState('')
  const [time, setTime] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const slots = useMemo(timeSlots, [])

  useEffect(() => {
    clinicApi.getReferenceData().then(setRefData).catch((err) => setError(errorMessage(err)))
  }, [])

  const selectedType = refData?.visitTypes.find((v) => v.code === visitType)

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    customerApi
      .bookAppointment({ visitType, clinician, startTime: `${date}T${time}` })
      .then(() => navigate('/customer'))
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
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/customer')}>
            Back
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Booking…' : 'Book visit'}
          </button>
        </div>
      </form>
    </div>
  )
}
