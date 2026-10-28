import { api } from './client'
import type { Appointment, CustomerAppointmentInput, Patient } from '../types'

// Customer portal: always the signed-in customer's own data
export const customerApi = {
  getProfile: () => api.get<Patient>('/customer/profile').then((r) => r.data),
  listAppointments: () => api.get<Appointment[]>('/customer/appointments').then((r) => r.data),
  bookAppointment: (input: CustomerAppointmentInput) =>
    api.post<Appointment>('/customer/appointments', input).then((r) => r.data),
  cancelAppointment: (id: number) =>
    api.patch<Appointment>(`/customer/appointments/${id}/cancel`).then((r) => r.data),
}
