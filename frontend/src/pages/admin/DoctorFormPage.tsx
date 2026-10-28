import { FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { clinicApi } from '../../api/clinicApi'
import { errorMessage } from '../../api/client'
import type { DoctorInput, ReferenceData } from '../../types'
import ErrorMessage from '../../components/ErrorMessage'

const empty: DoctorInput = {
  firstName: '',
  lastName: '',
  specialty: '',
  email: '',
  phone: '',
}

export default function DoctorFormPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<DoctorInput>(empty)
  const [refData, setRefData] = useState<ReferenceData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    clinicApi.getReferenceData().then(setRefData).catch((err) => setError(errorMessage(err)))
  }, [])

  const update = (field: keyof DoctorInput, value: string) => setForm({ ...form, [field]: value })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    clinicApi
      .createDoctor(form)
      .then(() => navigate('/admin/doctors'))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setSaving(false))
  }

  return (
    <div className="narrow">
      <div className="page-header">
        <div>
          <h1>Register doctor</h1>
          <p className="muted">The doctor can be booked as soon as they are registered. Use synthetic demo data only.</p>
        </div>
      </div>
      <ErrorMessage message={error} />
      <form className="card form" onSubmit={onSubmit}>
        <div className="form-row">
          <label>
            First name
            <input required maxLength={45} value={form.firstName} onChange={(e) => update('firstName', e.target.value)} />
          </label>
          <label>
            Last name
            <input required maxLength={45} value={form.lastName} onChange={(e) => update('lastName', e.target.value)} />
          </label>
        </div>
        {(form.firstName || form.lastName) && (
          <p className="muted">
            Shown to patients as <strong>Dr. {form.firstName.trim()} {form.lastName.trim()}</strong>
          </p>
        )}
        <div className="form-row">
          <label>
            Specialty
            <select required value={form.specialty} onChange={(e) => update('specialty', e.target.value)}>
              <option value="">Select…</option>
              {refData?.specialties.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Email
            <input type="email" required value={form.email} onChange={(e) => update('email', e.target.value)} />
          </label>
        </div>
        <label>
          Phone (optional)
          <input value={form.phone} onChange={(e) => update('phone', e.target.value)} />
        </label>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={() => navigate(-1)}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : 'Register doctor'}
          </button>
        </div>
      </form>
    </div>
  )
}
