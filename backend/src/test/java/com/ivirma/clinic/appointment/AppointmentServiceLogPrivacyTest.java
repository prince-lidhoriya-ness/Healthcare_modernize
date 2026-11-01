package com.ivirma.clinic.appointment;

import com.ivirma.clinic.TestDates;
import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.doctor.DoctorService;
import com.ivirma.clinic.patient.PatientRepository;
import com.ivirma.clinic.testsupport.LogCapture;
import com.ivirma.clinic.testsupport.SyntheticPatient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static com.ivirma.clinic.testsupport.SyntheticPatient.ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * STORY-024 (SEC-007, CWE-532, CWE-117; TASK-071, TASK-072): booking and status changes write the appointment
 * and patient ids and the action to the log, never the patient's name, date of birth, email or phone, nor the
 * visit type next to a person (health data, GDPR Art. 9), and request input cannot break a log line.
 * Synthetic data only.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("STORY-024 AppointmentService logs ids and actions only")
class AppointmentServiceLogPrivacyTest {

    private static final long APPOINTMENT_ID = 90210L;
    private static final String CLINICIAN = "Dr. Elena Ruiz";
    private static final VisitType VISIT = VisitType.EGG_RETRIEVAL;

    @RegisterExtension
    final LogCapture captured = LogCapture.watching(AppointmentService.class);

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorService doctorService;

    @InjectMocks
    private AppointmentService appointmentService;

    private static Map<String, Object> personalAndHealthData() {
        Map<String, Object> data = SyntheticPatient.personalData();
        data.put("visit type", VISIT);
        data.put("visit type label", VISIT.getLabel());
        return data;
    }

    private static AppointmentRequest booking(String clinician) {
        AppointmentRequest r = new AppointmentRequest();
        r.setPatientId(ID);
        r.setVisitType(VISIT);
        r.setClinician(clinician);
        r.setStartTime(TestDates.futureClinicDay(10, 0));
        return r;
    }

    private void saveAssignsId() {
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(APPOINTMENT_ID);
            return a;
        });
    }

    @Test
    @DisplayName("SEC-007 STORY-024 booking logs the appointment and patient ids, no personal data and no visit type")
    void SEC_007_STORY024_bookingLogsAppointmentAndPatientIdsOnly() {
        when(patientRepository.findById(ID)).thenReturn(Optional.of(SyntheticPatient.entity()));
        when(doctorService.isKnown(CLINICIAN)).thenReturn(true);
        saveAssignsId();

        appointmentService.schedule(booking(CLINICIAN));

        captured.assertInfoLineShows(APPOINTMENT_ID, ID);
        captured.assertNoLineCarries(personalAndHealthData());
    }

    @Test
    @DisplayName("SEC-007 STORY-024 status change logs the appointment and patient ids and the new status, no personal data")
    void SEC_007_STORY024_statusChangeLogsAppointmentAndPatientIdsOnly() {
        Appointment appointment = new Appointment();
        appointment.setId(APPOINTMENT_ID);
        appointment.setPatient(SyntheticPatient.entity());
        appointment.setVisitType(VISIT);
        appointment.setClinician(CLINICIAN);
        appointment.setStartTime(LocalDateTime.of(2026, 10, 30, 10, 0));
        appointment.setDurationMinutes(VISIT.getDurationMinutes());
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        saveAssignsId();

        appointmentService.updateStatus(APPOINTMENT_ID, AppointmentStatus.COMPLETED);

        captured.assertInfoLineShows(APPOINTMENT_ID, ID);
        captured.assertNoLineCarries(personalAndHealthData());
    }

    @Test
    @DisplayName("CWE-117 STORY-024 CR/LF in the clinician of a booking cannot start a forged log line")
    void CWE_117_STORY024_lineBreaksInBookingInputCannotForgeALogLine() {
        String forgedClinician = "Dr. Ada Example\r\n2026-11-02 10:00:00 INFO forged entry";
        when(patientRepository.findById(ID)).thenReturn(Optional.of(SyntheticPatient.entity()));
        when(doctorService.isKnown(forgedClinician)).thenReturn(true);
        saveAssignsId();

        appointmentService.schedule(booking(forgedClinician));

        captured.assertNoLineBreaks();
        captured.assertNoLineCarries(Map.of("forged request text", "forged"));
    }
}
