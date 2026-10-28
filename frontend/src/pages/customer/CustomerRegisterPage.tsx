import { FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { clinicApi } from '../../api/clinicApi'
import { errorMessage } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import type { CustomerSignupInput, ReferenceData } from '../../types'
import Brand from '../../components/Brand'
import ErrorMessage from '../../components/ErrorMessage'

const empty: CustomerSignupInput = {
  firstName: '',
  lastName: '',
  dateOfBirth: '',
  email: '',
  phone: '',
  treatmentInterest: '',
  password: '',
}

export default function CustomerRegisterPage() {
  const { registerCustomer } = useAuth()
  const navigate = useNavigate()
  const [form, setForm] = useState<CustomerSignupInput>(empty)
  const [confirm, setConfirm] = useState('')
  const [refData, setRefData] = useState<ReferenceData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    clinicApi.getReferenceData().then(setRefData).catch((err) => setError(errorMessage(err)))
  }, [])

  const update = (field: keyof CustomerSignupInput, value: string) => setForm({ ...form, [field]: value })

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (form.password !== confirm) {
      setError('The passwords do not match')
      return
    }
    setSaving(true)
    setError(null)
    registerCustomer(form)
      .then(() => navigate('/customer', { replace: true }))
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setSaving(false))
  }

  return (
    <div className="auth-screen">
      <Brand portal="Customer portal" />
      <div className="narrow">
        <div className="page-header">
          <div>
            <h1>Create your account</h1>
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
              <input
                type="email"
                required
                autoComplete="username"
                value={form.email}
                onChange={(e) => update('email', e.target.value)}
              />
            </label>
            <label>
              Phone (optional)
              <input value={form.phone} onChange={(e) => update('phone', e.target.value)} />
            </label>
          </div>
          <div className="form-row">
            <label>
              Password
              <input
                type="password"
                required
                minLength={8}
                autoComplete="new-password"
                value={form.password}
                onChange={(e) => update('password', e.target.value)}
              />
            </label>
            <label>
              Confirm password
              <input
                type="password"
                required
                autoComplete="new-password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
              />
            </label>
          </div>
          <p className="muted">At least 8 characters.</p>
          <div className="form-actions">
            <Link className="btn btn-ghost" to="/customer/login">
              I already have an account
            </Link>
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Creating…' : 'Create account'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
