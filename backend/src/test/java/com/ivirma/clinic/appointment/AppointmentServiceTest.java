package com.ivirma.clinic.appointment;

import com.ivirma.clinic.TestDates;
import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.BadRequestException;
import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.TreatmentInterest;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final String CLINICIAN = "Dr. Elena Ruiz";

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    private Patient patient;

    @BeforeEach
    void setUp() {
        patient = new Patient();
        patient.setId(7L);
        patient.setFirstName("Test");
        patient.setLastName("Patient");
        patient.setDateOfBirth(LocalDate.of(1990, 1, 1));
        patient.setEmail("test@example.com");
        patient.setTreatmentInterest(TreatmentInterest.IVF);
        lenient().when(patientRepository.findById(7L)).thenReturn(Optional.of(patient));
        lenient().when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });
    }

    private AppointmentRequest request(VisitType type, LocalDateTime start) {
        AppointmentRequest r = new AppointmentRequest();
        r.setPatientId(7L);
        r.setVisitType(type);
        r.setClinician(CLINICIAN);
        r.setStartTime(start);
        return r;
    }

    private Appointment existing(LocalDateTime start, VisitType type) {
        Appointment a = new Appointment();
        a.setId(1L);
        a.setPatient(patient);
        a.setClinician(CLINICIAN);
        a.setVisitType(type);
        a.setStartTime(start);
        a.setDurationMinutes(type.getDurationMinutes());
        a.setStatus(AppointmentStatus.SCHEDULED);
        return a;
    }

    @Test
    void scheduleSavesAppointmentWithDurationFromVisitType() {
        LocalDateTime start = TestDates.futureClinicDay(10, 0);

        AppointmentResponse result = appointmentService.schedule(request(VisitType.EGG_RETRIEVAL, start));

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(result.getDurationMinutes()).isEqualTo(60);
        assertThat(result.getEndTime()).isEqualTo(start.plusMinutes(60));
        assertThat(result.getPatientName()).isEqualTo("Test Patient");
    }

    @Test
    void scheduleRejectsClinicianDoubleBooking() {
        LocalDateTime start = TestDates.futureClinicDay(10, 0);
        when(appointmentRepository.findByClinicianAndStatusNotAndStartTimeBetween(
                eq(CLINICIAN), eq(AppointmentStatus.CANCELLED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(existing(start.minusMinutes(15), VisitType.INITIAL_CONSULTATION)));

        assertThatThrownBy(() -> appointmentService.schedule(request(VisitType.BLOOD_TEST, start)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(CLINICIAN);
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void scheduleAllowsBackToBackAppointments() {
        LocalDateTime start = TestDates.futureClinicDay(10, 0);
        when(appointmentRepository.findByClinicianAndStatusNotAndStartTimeBetween(
                eq(CLINICIAN), eq(AppointmentStatus.CANCELLED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(existing(start.minusMinutes(30), VisitType.FOLLOW_UP)));

        AppointmentResponse result = appointmentService.schedule(request(VisitType.BLOOD_TEST, start));
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void scheduleRejectsPatientDoubleBooking() {
        LocalDateTime start = TestDates.futureClinicDay(11, 0);
        when(appointmentRepository.findByPatientIdAndStatusNotAndStartTimeBetween(
                eq(7L), eq(AppointmentStatus.CANCELLED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(existing(start, VisitType.BLOOD_TEST)));

        assertThatThrownBy(() -> appointmentService.schedule(request(VisitType.FOLLOW_UP, start)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Patient");
    }

    @Test
    void scheduleRejectsPastTime() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        assertThatThrownBy(() -> appointmentService.schedule(request(VisitType.BLOOD_TEST, past)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("past");
    }

    @Test
    void scheduleRejectsSunday() {
        assertThatThrownBy(() -> appointmentService.schedule(request(VisitType.BLOOD_TEST, TestDates.nextSunday(10))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Sunday");
    }

    @Test
    void scheduleRejectsOutsideOpeningHours() {
        LocalDateTime late = TestDates.futureClinicDay(18, 45);
        assertThatThrownBy(() -> appointmentService.schedule(request(VisitType.INITIAL_CONSULTATION, late)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("08:00");
    }

    @Test
    void scheduleRejectsUnknownClinician() {
        AppointmentRequest r = request(VisitType.BLOOD_TEST, TestDates.futureClinicDay(10, 0));
        r.setClinician("Dr. Nobody");
        assertThatThrownBy(() -> appointmentService.schedule(r))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateStatusMarksScheduledAppointmentCompleted() {
        Appointment a = existing(TestDates.futureClinicDay(9, 0), VisitType.BLOOD_TEST);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        AppointmentResponse result = appointmentService.updateStatus(1L, AppointmentStatus.COMPLETED);
        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void updateStatusRejectsChangeOfCancelledAppointment() {
        Appointment a = existing(TestDates.futureClinicDay(9, 0), VisitType.BLOOD_TEST);
        a.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> appointmentService.updateStatus(1L, AppointmentStatus.COMPLETED))
                .isInstanceOf(ConflictException.class);
    }
}
