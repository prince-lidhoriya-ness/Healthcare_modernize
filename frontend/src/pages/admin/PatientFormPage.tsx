import { FormEvent, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { clinicApi } from '../../api/clinicApi'
import { errorMessage } from '../../api/client'
import type { PatientInput, ReferenceData } from '../../types'
import ErrorMessage from '../../components/ErrorMessage'

const empty: PatientInput = {
  firstName: '',
  lastName: '',
  dateOfBirth: '',
  email: '',
  phone: '',
  treatmentInterest: '',
}

export default function PatientFormPage() {
  const { id } = useParams()
  const editing = Boolean(id)
  const navigate = useNavigate()
  const [form, setForm] = useState<PatientInput>(empty)
  const [refData, setRefData] = useState<ReferenceData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    clinicApi.getReferenceData().then(setRefData).catch((err) => setError(errorMessage(err)))
    if (id) {
      clinicApi
        .getPatient(Number(id))
        .then((p) =>
          setForm({
            firstName: p.firstName,
            lastName: p.lastName,
            dateOfBirth: p.dateOfBirth,
            email: p.email,
            phone: p.phone || '',
            treatmentInterest: p.treatmentInterest,
          }),
        )
        .catch((err) => setError(errorMessage(err)))
    }
  }, [id])

  const update = (field: keyof PatientInput, value: string) => setForm({ ...form, [field]: value })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    setSaving(true)
    setError(null)
    const request = editing ? clinicApi.updatePatient(Number(id), form) : clinicApi.createPatient(form)
    request
      .then((saved) => navigate(`/admin/patients/${saved.id}`))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setSaving(false))
  }

  return (
    <div className="narrow">
      <div className="page-header">
        <div>
          <h1>{editing ? 'Edit patient' : 'Register patient'}</h1>
          <p className="muted">Use synthetic demo data only.</p>
        </div>
      </div>
      <ErrorMessage message={error} />
      <form className="card form" onSubmit={onSubmit}>
        <div className="form-row">
          <label>
            First name
            <input required value={form.firstName} onChange={(e) => update('firstName', e.target.value)} />
          </label>
          <label>
            Last name
            <input required value={form.lastName} onChange={(e) => update('lastName', e.target.value)} />
          </label>
        </div>
        <div className="form-row">
          <label>
            Date of birth
            <input
              type="date"
              required
              value={form.dateOfBirth}
              onChange={(e) => update('dateOfBirth', e.target.value)}
            />
          </label>
          <label>
            Treatment interest
            <select
              required
              value={form.treatmentInterest}
              onChange={(e) => update('treatmentInterest', e.target.value)}
            >
              <option value="">Select…</option>
              {refData?.treatmentInterests.map((t) => (
                <option key={t.code} value={t.code}>
                  {t.label}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className="form-row">
          <label>
            Email
            <input type="email" required value={form.email} onChange={(e) => update('email', e.target.value)} />
          </label>
          <label>
            Phone (optional)
            <input value={form.phone} onChange={(e) => update('phone', e.target.value)} />
          </label>
        </div>
        <div className="form-actions">
          <button type="button" className="btn btn-ghost" onClick={() => navigate(-1)}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? 'Saving…' : editing ? 'Save changes' : 'Register patient'}
          </button>
        </div>
      </form>
    </div>
  )
}
