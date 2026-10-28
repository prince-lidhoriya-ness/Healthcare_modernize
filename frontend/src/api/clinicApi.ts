import { api } from './client'
import type {
  Appointment,
  AppointmentInput,
  AppointmentStatus,
  DashboardSummary,
  Doctor,
  DoctorInput,
  Patient,
  PatientInput,
  ReferenceData,
} from '../types'

export const clinicApi = {
  getReferenceData: () => api.get<ReferenceData>('/reference-data').then((r) => r.data),

  listPatients: (search?: string) =>
    api.get<Patient[]>('/patients', { params: search ? { search } : {} }).then((r) => r.data),
  getPatient: (id: number) => api.get<Patient>(`/patients/${id}`).then((r) => r.data),
  createPatient: (input: PatientInput) => api.post<Patient>('/patients', input).then((r) => r.data),
  updatePatient: (id: number, input: PatientInput) =>
    api.put<Patient>(`/patients/${id}`, input).then((r) => r.data),
  listPatientAppointments: (id: number) =>
    api.get<Appointment[]>(`/patients/${id}/appointments`).then((r) => r.data),

  listUpcomingAppointments: () =>
    api.get<Appointment[]>('/appointments', { params: { upcoming: true } }).then((r) => r.data),
  createAppointment: (input: AppointmentInput) =>
    api.post<Appointment>('/appointments', input).then((r) => r.data),
  updateAppointmentStatus: (id: number, status: AppointmentStatus) =>
    api.patch<Appointment>(`/appointments/${id}/status`, { status }).then((r) => r.data),

  listDoctors: () => api.get<Doctor[]>('/doctors').then((r) => r.data),
  createDoctor: (input: DoctorInput) => api.post<Doctor>('/doctors', input).then((r) => r.data),

  getDashboard: () => api.get<DashboardSummary>('/dashboard/summary').then((r) => r.data),
}
