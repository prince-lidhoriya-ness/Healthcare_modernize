import { FormEvent, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { clinicApi } from '../api/clinicApi'
import { errorMessage } from '../api/client'
import type { Patient } from '../types'
import { formatDate, interestLabel } from '../format'
import ErrorMessage from '../components/ErrorMessage'

export default function PatientsPage() {
  const [patients, setPatients] = useState<Patient[]>([])
  const [search, setSearch] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = (term?: string) => {
    setLoading(true)
    setError(null)
    clinicApi
      .listPatients(term)
      .then(setPatients)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  const onSearch = (e: FormEvent) => {
    e.preventDefault()
    load(search)
  }

  return (
    <div>
      <div className="page-header">
        <div>
          <h1>Patients</h1>
          <p className="muted">{patients.length} shown</p>
        </div>
        <Link className="btn btn-primary" to="/patients/new">
          Register patient
        </Link>
      </div>
      <form className="search-bar" onSubmit={onSearch}>
        <input
          aria-label="Search patients"
          placeholder="Search by name or email"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button className="btn" type="submit">
          Search
        </button>
      </form>
      <ErrorMessage message={error} />
      <div className="card">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : patients.length === 0 ? (
          <p className="muted">No patients found.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Date of birth</th>
                  <th>Email</th>
                  <th>Treatment interest</th>
                </tr>
              </thead>
              <tbody>
                {patients.map((p) => (
                  <tr key={p.id}>
                    <td>
                      <Link to={`/patients/${p.id}`}>
                        {p.firstName} {p.lastName}
                      </Link>
                    </td>
                    <td>{formatDate(p.dateOfBirth)}</td>
                    <td>{p.email}</td>
                    <td>
                      <span className="chip">{interestLabel(p.treatmentInterest)}</span>
                    </td>
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
