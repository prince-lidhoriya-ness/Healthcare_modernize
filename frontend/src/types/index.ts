export type TreatmentInterest = 'FERTILITY_ASSESSMENT' | 'IVF' | 'IUI' | 'EGG_FREEZING' | 'EGG_DONATION'
export type VisitType =
  | 'INITIAL_CONSULTATION'
  | 'ULTRASOUND_MONITORING'
  | 'BLOOD_TEST'
  | 'EGG_RETRIEVAL'
  | 'EMBRYO_TRANSFER'
  | 'FOLLOW_UP'
export type AppointmentStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW'

export interface Patient {
  id: number
  firstName: string
  lastName: string
  dateOfBirth: string
  email: string
  phone?: string
  treatmentInterest: TreatmentInterest
  createdAt: string
}

export interface PatientInput {
  firstName: string
  lastName: string
  dateOfBirth: string
  email: string
  phone?: string
  treatmentInterest: TreatmentInterest | ''
}

export interface Appointment {
  id: number
  patientId: number
  patientName: string
  visitType: VisitType
  visitTypeLabel: string
  clinician: string
  startTime: string
  endTime: string
  durationMinutes: number
  status: AppointmentStatus
}

export interface AppointmentInput {
  patientId: number | ''
  visitType: VisitType | ''
  clinician: string
  startTime: string
}

export interface ReferenceData {
  visitTypes: { code: VisitType; label: string; durationMinutes: number }[]
  treatmentInterests: { code: TreatmentInterest; label: string }[]
  appointmentStatuses: AppointmentStatus[]
  clinicians: string[]
}

export interface DashboardSummary {
  totalPatients: number
  newPatientsThisWeek: number
  appointmentsToday: number
  upcomingNext7Days: number
  appointmentsByStatus: Record<string, number>
  appointmentsByVisitType: Record<string, number>
  nextAppointments: Appointment[]
  generatedAt: string
}
