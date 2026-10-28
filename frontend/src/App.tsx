import { Navigate, Route, Routes } from 'react-router-dom'
import RequireRole from './auth/RequireRole'
import PortalLayout from './layouts/PortalLayout'
import PortalChooserPage from './pages/PortalChooserPage'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/admin/DashboardPage'
import PatientsPage from './pages/admin/PatientsPage'
import DoctorsPage from './pages/admin/DoctorsPage'
import DoctorFormPage from './pages/admin/DoctorFormPage'
import PatientFormPage from './pages/admin/PatientFormPage'
import PatientDetailPage from './pages/admin/PatientDetailPage'
import AppointmentsPage from './pages/admin/AppointmentsPage'
import AppointmentFormPage from './pages/admin/AppointmentFormPage'
import CustomerRegisterPage from './pages/customer/CustomerRegisterPage'
import CustomerHomePage from './pages/customer/CustomerHomePage'
import CustomerBookPage from './pages/customer/CustomerBookPage'

const adminLinks = [
  { to: '/admin', label: 'Dashboard', end: true },
  { to: '/admin/patients', label: 'Patients' },
  { to: '/admin/doctors', label: 'Doctors' },
  { to: '/admin/appointments', label: 'Appointments' },
]

const customerLinks = [
  { to: '/customer', label: 'My visits', end: true },
  { to: '/customer/book', label: 'Book a visit' },
]

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<PortalChooserPage />} />

      {/* Admin portal: clinic staff */}
      <Route path="/admin/login" element={<LoginPage portal="ADMIN" />} />
      <Route
        path="/admin"
        element={
          <RequireRole role="ADMIN" loginPath="/admin/login">
            <PortalLayout portalName="Admin portal" links={adminLinks} loginPath="/admin/login" />
          </RequireRole>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="patients" element={<PatientsPage />} />
        <Route path="patients/:id" element={<PatientDetailPage />} />
        <Route path="patients/:id/edit" element={<PatientFormPage />} />
        <Route path="doctors" element={<DoctorsPage />} />
        <Route path="doctors/new" element={<DoctorFormPage />} />
        <Route path="appointments" element={<AppointmentsPage />} />
        <Route path="appointments/new" element={<AppointmentFormPage />} />
      </Route>

      {/* Customer portal: patients */}
      <Route path="/customer/login" element={<LoginPage portal="CUSTOMER" />} />
      <Route path="/customer/register" element={<CustomerRegisterPage />} />
      <Route
        path="/customer"
        element={
          <RequireRole role="CUSTOMER" loginPath="/customer/login">
            <PortalLayout portalName="Customer portal" links={customerLinks} loginPath="/customer/login" />
          </RequireRole>
        }
      >
        <Route index element={<CustomerHomePage />} />
        <Route path="book" element={<CustomerBookPage />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
