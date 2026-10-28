import { NavLink, Route, Routes } from 'react-router-dom'
import DashboardPage from './pages/DashboardPage'
import PatientsPage from './pages/PatientsPage'
import PatientFormPage from './pages/PatientFormPage'
import PatientDetailPage from './pages/PatientDetailPage'
import AppointmentsPage from './pages/AppointmentsPage'
import AppointmentFormPage from './pages/AppointmentFormPage'

export default function App() {
  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true" />
          <span>Fertility Clinic</span>
          <span className="demo-tag">Demo · synthetic data</span>
        </div>
        <nav>
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/patients">Patients</NavLink>
          <NavLink to="/appointments">Appointments</NavLink>
        </nav>
      </header>
      <main className="content">
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/patients" element={<PatientsPage />} />
          <Route path="/patients/new" element={<PatientFormPage />} />
          <Route path="/patients/:id" element={<PatientDetailPage />} />
          <Route path="/patients/:id/edit" element={<PatientFormPage />} />
          <Route path="/appointments" element={<AppointmentsPage />} />
          <Route path="/appointments/new" element={<AppointmentFormPage />} />
        </Routes>
      </main>
    </div>
  )
}
