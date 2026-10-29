package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.BadRequestException;
import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.NotFoundException;
import com.ivirma.clinic.common.TreatmentInterest;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.doctor.DoctorRepository;
import com.ivirma.clinic.doctor.DoctorService;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * STORY-001 characterization of {@link AppointmentService}: pins what the legacy code does today
 * (not what it should do). The server clock is frozen at Thu 2026-10-29 10:00:30 with a static mock
 * of {@code LocalDateTime.now()}, so every expectation is a literal date from BUSINESS_RULES.md.
 *
 * <p>Conflict lookups are answered by a tiny in-memory "database" that applies the same filter the
 * Spring Data query does (clinician or patient, status != excluded, start BETWEEN from AND to,
 * inclusive), so the 60-minute look-back quirk of RULE-003 is observable.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("STORY-001 AppointmentService characterization")
class AppointmentServiceCharacterizationTest {

    /** Thu 2026-10-29 10:00:30, the "today" used by BUSINESS_RULES.md examples. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 10, 0, 30);
    private static final LocalDate SAT = LocalDate.of(2026, 10, 31);
    private static final LocalDate SUN = LocalDate.of(2026, 11, 1);
    private static final LocalDate MON = LocalDate.of(2026, 11, 2);

    private static final String RUIZ = "Dr. Elena Ruiz";
    private static final String BIANCHI = "Dr. Marco Bianchi";

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    private AppointmentService service;
    private MockedStatic<LocalDateTime> clock;
    private Patient laura;
    private final List<Appointment> db = new ArrayList<Appointment>();

    @BeforeEach
    void setUp() {
        clock = mockStatic(LocalDateTime.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS).strictness(Strictness.LENIENT));
        clock.when(LocalDateTime::now).thenReturn(NOW);

        // real DoctorService over a mocked repository, so RULE-016's exact-name lookup is exercised
        service = new AppointmentService(appointmentRepository, patientRepository, new DoctorService(doctorRepository));

        laura = new Patient();
        laura.setId(7L);
        laura.setFirstName("Laura");
        laura.setLastName("Gomez");
        laura.setDateOfBirth(LocalDate.of(1989, 4, 12));
        laura.setEmail("laura.gomez@example.com");
        laura.setTreatmentInterest(TreatmentInterest.IVF);

        lenient().when(patientRepository.findById(7L)).thenReturn(Optional.of(laura));
        lenient().when(doctorRepository.existsByName(anyString())).thenAnswer(inv ->
                RUIZ.equals(inv.getArgument(0)) || BIANCHI.equals(inv.getArgument(0)));
        lenient().when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(100L);
            }
            return a;
        });
        lenient().when(appointmentRepository.findByClinicianAndStatusNotAndStartTimeBetween(
                anyString(), any(AppointmentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> select(inv.getArgument(0), null, inv.getArgument(1), inv.getArgument(2),
                        inv.getArgument(3)));
        lenient().when(appointmentRepository.findByPatientIdAndStatusNotAndStartTimeBetween(
                anyLong(), any(AppointmentStatus.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> select(null, inv.getArgument(0), inv.getArgument(1), inv.getArgument(2),
                        inv.getArgument(3)));
    }

    @AfterEach
    void tearDown() {
        clock.close();
    }

    // ---------------------------------------------------------------- helpers

    private List<Appointment> select(String clinician, Long patientId, AppointmentStatus excluded,
                                     LocalDateTime from, LocalDateTime to) {
        List<Appointment> result = new ArrayList<Appointment>();
        for (Appointment a : db) {
            boolean who = clinician != null ? clinician.equals(a.getClinician())
                    : patientId.equals(a.getPatient().getId());
            boolean inRange = !a.getStartTime().isBefore(from) && !a.getStartTime().isAfter(to);
            if (who && a.getStatus() != excluded && inRange) {
                result.add(a);
            }
        }
        return result;
    }

    private static LocalDateTime at(LocalDate day, int hour, int minute) {
        return LocalDateTime.of(day, LocalTime.of(hour, minute));
    }

    private AppointmentRequest request(VisitType type, String clinician, LocalDateTime start) {
        AppointmentRequest r = new AppointmentRequest();
        r.setPatientId(7L);
        r.setVisitType(type);
        r.setClinician(clinician);
        r.setStartTime(start);
        return r;
    }

    private Appointment row(long id, Patient patient, String clinician, VisitType type, LocalDateTime start,
                            int durationMinutes, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatient(patient);
        a.setClinician(clinician);
        a.setVisitType(type);
        a.setStartTime(start);
        a.setDurationMinutes(durationMinutes);
        a.setStatus(status);
        a.setCreatedAt(NOW.minusDays(10));
        return a;
    }

    private Patient otherPatient() {
        Patient p = new Patient();
        p.setId(8L);
        p.setFirstName("Sofia");
        p.setLastName("Romano");
        return p;
    }

    // ---------------------------------------------------------------- RULE-001

    @ParameterizedTest(name = "RULE-001 STORY-001 {0} lasts {1} minutes and is labelled \"{2}\"")
    @CsvSource({
            "INITIAL_CONSULTATION, 45, Initial consultation",
            "ULTRASOUND_MONITORING, 20, Ultrasound monitoring",
            "BLOOD_TEST, 15, Blood test",
            "EGG_RETRIEVAL, 60, Egg retrieval",
            "EMBRYO_TRANSFER, 30, Embryo transfer",
            "FOLLOW_UP, 30, Follow-up"
    })
    void RULE_001_STORY001_visitTypeDurationDecidesEndTime(VisitType type, int minutes, String label) {
        AppointmentResponse result = service.schedule(request(type, RUIZ, at(MON, 10, 0)));

        assertThat(type.getDurationMinutes()).isEqualTo(minutes);
        assertThat(type.getLabel()).isEqualTo(label);
        assertThat(result.getDurationMinutes()).isEqualTo(minutes);
        assertThat(result.getVisitTypeLabel()).isEqualTo(label);
        assertThat(result.getEndTime()).isEqualTo(at(MON, 10, 0).plusMinutes(minutes));
    }

    @Test
    @DisplayName("RULE-001 STORY-001 there are exactly six visit types, in this order")
    void RULE_001_STORY001_exactlySixVisitTypesInDeclarationOrder() {
        assertThat(VisitType.values()).containsExactly(VisitType.INITIAL_CONSULTATION,
                VisitType.ULTRASOUND_MONITORING, VisitType.BLOOD_TEST, VisitType.EGG_RETRIEVAL,
                VisitType.EMBRYO_TRANSFER, VisitType.FOLLOW_UP);
    }

    // ---------------------------------------------------------------- RULE-002

    @Test
    @DisplayName("RULE-002 STORY-001 10:00:45.123 is stored as 10:00 and an Initial consultation ends 10:45")
    void RULE_002_STORY001_startTruncatedToMinuteAndEndIsStartPlusDuration() {
        LocalDateTime requested = LocalDateTime.of(2026, 11, 2, 10, 0, 45, 123_000_000);

        AppointmentResponse result = service.schedule(request(VisitType.INITIAL_CONSULTATION, RUIZ, requested));

        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(saved.capture());
        assertThat(saved.getValue().getStartTime()).isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 0, 0, 0));
        assertThat(saved.getValue().getDurationMinutes()).isEqualTo(45);
        assertThat(saved.getValue().getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(result.getStartTime()).isEqualTo(at(MON, 10, 0));
        assertThat(result.getEndTime()).isEqualTo(at(MON, 10, 45));
    }

    // ---------------------------------------------------------------- RULE-003

    @Test
    @DisplayName("RULE-003 STORY-001 a 10:00-10:45 visit looks up conflicts starting 09:00..10:45 for clinician and patient")
    void RULE_003_STORY001_conflictWindowStartsSixtyMinutesBeforeNewStart() {
        service.schedule(request(VisitType.INITIAL_CONSULTATION, RUIZ, at(MON, 10, 0)));

        verify(appointmentRepository).findByClinicianAndStatusNotAndStartTimeBetween(
                RUIZ, AppointmentStatus.CANCELLED, at(MON, 9, 0), at(MON, 10, 45));
        verify(appointmentRepository).findByPatientIdAndStatusNotAndStartTimeBetween(
                7L, AppointmentStatus.CANCELLED, at(MON, 9, 0), at(MON, 10, 45));
    }

    @Test
    @DisplayName("RULE-003 STORY-001 the 60-minute look-back does not depend on the new visit type (Blood test 10:00 -> 09:00..10:15)")
    void RULE_003_STORY001_lookBackIsFixedSixtyMinutesForEveryVisitType() {
        service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 10, 0)));

        verify(appointmentRepository).findByClinicianAndStatusNotAndStartTimeBetween(
                RUIZ, AppointmentStatus.CANCELLED, at(MON, 9, 0), at(MON, 10, 15));
    }

    @Test
    @DisplayName("RULE-003 STORY-001 quirk: a stored 90-minute visit 08:00-09:30 is NOT seen by a 09:05 booking")
    void RULE_003_STORY001_storedVisitLongerThanSixtyMinutesIsMissedByLookBack() {
        // A row whose stored duration exceeds 60 (possible if a longer visit type is ever added)
        db.add(row(1L, laura, RUIZ, VisitType.EGG_RETRIEVAL, at(MON, 8, 0), 90, AppointmentStatus.SCHEDULED));

        AppointmentResponse result = service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 9, 5)));

        assertThat(result.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED); // overlap silently accepted
    }

    @Test
    @DisplayName("RULE-003 STORY-001 a stored 90-minute visit 08:00-09:30 starting exactly 60 minutes before a 09:00 booking is seen (inclusive bound)")
    void RULE_003_STORY001_windowLowerBoundIsInclusive() {
        db.add(row(1L, otherPatient(), RUIZ, VisitType.EGG_RETRIEVAL, at(MON, 8, 0), 90,
                AppointmentStatus.SCHEDULED));

        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 9, 0))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Dr. Elena Ruiz already has an appointment from 08:00 to 09:30");
    }

    // ---------------------------------------------------------------- RULE-016

    @Test
    @DisplayName("RULE-016 STORY-001 'dr. elena ruiz' (wrong case) -> 400 Unknown clinician: dr. elena ruiz")
    void RULE_016_STORY001_clinicianNameMatchIsCaseSensitive() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, "dr. elena ruiz", at(MON, 10, 0))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown clinician: dr. elena ruiz");
        verify(doctorRepository).existsByName("dr. elena ruiz");
        verify(doctorRepository, never()).existsByNameIgnoreCase(anyString());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("RULE-016 STORY-001 a trailing space is not trimmed: 'Dr. Elena Ruiz ' is unknown")
    void RULE_016_STORY001_clinicianNameIsNotTrimmed() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, "Dr. Elena Ruiz ", at(MON, 10, 0))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown clinician: Dr. Elena Ruiz ");
    }

    @Test
    @DisplayName("RULE-016 STORY-001 the clinician check runs before the date checks (unknown clinician on a Sunday)")
    void RULE_016_STORY001_clinicianCheckedBeforeDateRules() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, "Dr. Nobody", at(SUN, 10, 0))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown clinician: Dr. Nobody");
    }

    @Test
    @DisplayName("RULE-016 STORY-001 an unknown patient is reported before an unknown clinician (404 Patient 999 not found)")
    void RULE_016_STORY001_patientLookupPrecedesClinicianCheck() {
        AppointmentRequest r = request(VisitType.BLOOD_TEST, "Dr. Nobody", at(MON, 10, 0));
        r.setPatientId(999L);

        assertThatThrownBy(() -> service.schedule(r))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Patient 999 not found");
    }

    // ---------------------------------------------------------------- RULE-017

    @Test
    @DisplayName("RULE-017 STORY-001 now 10:00:30: a 10:00 booking is refused as past")
    void RULE_017_STORY001_currentMinuteIsRefusedOnceItsZeroSecondHasPassed() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(NOW.toLocalDate(), 10, 0))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointments cannot be booked in the past");
    }

    @Test
    @DisplayName("RULE-017 STORY-001 now 10:00:30: a 10:00:59 request truncates to 10:00 and is refused")
    void RULE_017_STORY001_truncationHappensBeforeThePastCheck() {
        LocalDateTime requested = LocalDateTime.of(2026, 10, 29, 10, 0, 59);
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, requested)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointments cannot be booked in the past");
    }

    @Test
    @DisplayName("RULE-017 STORY-001 now 10:00:30: a 10:01 booking (zero notice) is accepted")
    void RULE_017_STORY001_nextMinuteIsAcceptedWithZeroNotice() {
        AppointmentResponse result = service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(NOW.toLocalDate(), 10, 1)));

        assertThat(result.getStartTime()).isEqualTo(LocalDateTime.of(2026, 10, 29, 10, 1));
    }

    // ---------------------------------------------------------------- RULE-018

    @Test
    @DisplayName("RULE-018 STORY-001 Sunday 2026-11-01 10:00 -> 400 The clinic is closed on Sundays")
    void RULE_018_STORY001_sundayIsRefused() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(SUN, 10, 0))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The clinic is closed on Sundays");
    }

    @Test
    @DisplayName("RULE-018 STORY-001 the Sunday check runs before the opening-hours check (Sunday 07:00)")
    void RULE_018_STORY001_sundayCheckPrecedesHoursCheck() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(SUN, 7, 0))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The clinic is closed on Sundays");
    }

    @Test
    @DisplayName("RULE-018 STORY-001 Saturday uses the same hours: Egg retrieval 18:00-19:00 on Sat 2026-10-31 is accepted")
    void RULE_018_STORY001_saturdayIsOpenUntil1900() {
        AppointmentResponse result = service.schedule(request(VisitType.EGG_RETRIEVAL, RUIZ, at(SAT, 18, 0)));

        assertThat(result.getEndTime()).isEqualTo(at(SAT, 19, 0));
    }

    // ---------------------------------------------------------------- RULE-019

    @Test
    @DisplayName("RULE-019 STORY-001 a visit starting exactly 08:00 is accepted")
    void RULE_019_STORY001_visitMayStartAt0800() {
        assertThat(service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 8, 0))).getStartTime())
                .isEqualTo(at(MON, 8, 0));
    }

    @Test
    @DisplayName("RULE-019 STORY-001 07:59 -> 400 Appointments must be between 08:00 and 19:00")
    void RULE_019_STORY001_visitMustNotStartBefore0800() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 7, 59))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointments must be between 08:00 and 19:00");
    }

    @Test
    @DisplayName("RULE-019 STORY-001 Initial consultation at 18:15 ends exactly 19:00 and is accepted")
    void RULE_019_STORY001_visitMayEndExactlyAt1900() {
        AppointmentResponse result = service.schedule(request(VisitType.INITIAL_CONSULTATION, RUIZ, at(MON, 18, 15)));

        assertThat(result.getEndTime()).isEqualTo(at(MON, 19, 0));
    }

    @Test
    @DisplayName("RULE-019 STORY-001 Initial consultation at 18:30 ends 19:15 -> 400")
    void RULE_019_STORY001_visitMustEndBy1900() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.INITIAL_CONSULTATION, RUIZ, at(MON, 18, 30))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointments must be between 08:00 and 19:00");
    }

    @Test
    @DisplayName("RULE-019 STORY-001 a Blood test at 23:50 ends 00:05 the next day -> 400 (cross-midnight branch)")
    void RULE_019_STORY001_visitMustEndOnTheDayItStarts() {
        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 23, 50))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Appointments must be between 08:00 and 19:00");
    }

    // ---------------------------------------------------------------- RULE-020

    @Test
    @DisplayName("RULE-020 STORY-001 Ruiz 09:45-10:30 booked; Blood test at 10:00 -> 409 with the exact message")
    void RULE_020_STORY001_overlappingClinicianVisitIsRefused() {
        db.add(row(1L, otherPatient(), RUIZ, VisitType.INITIAL_CONSULTATION, at(MON, 9, 45), 45,
                AppointmentStatus.SCHEDULED));

        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 10, 0))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Dr. Elena Ruiz already has an appointment from 09:45 to 10:30");
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("RULE-020 STORY-001 back-to-back after: a 10:30 booking after 09:45-10:30 is accepted")
    void RULE_020_STORY001_bookingStartingAtExistingEndIsAccepted() {
        db.add(row(1L, otherPatient(), RUIZ, VisitType.INITIAL_CONSULTATION, at(MON, 9, 45), 45,
                AppointmentStatus.SCHEDULED));

        assertThat(service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 10, 30))).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("RULE-020 STORY-001 back-to-back before: a Blood test 09:30-09:45 before 09:45-10:30 is accepted")
    void RULE_020_STORY001_bookingEndingAtExistingStartIsAccepted() {
        db.add(row(1L, otherPatient(), RUIZ, VisitType.INITIAL_CONSULTATION, at(MON, 9, 45), 45,
                AppointmentStatus.SCHEDULED));

        assertThat(service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 9, 30))).getId()).isEqualTo(100L);
    }

    @ParameterizedTest(name = "RULE-020 STORY-001 an existing {0} visit still blocks the slot")
    @EnumSource(value = AppointmentStatus.class, names = {"SCHEDULED", "COMPLETED", "NO_SHOW"})
    void RULE_020_STORY001_nonCancelledVisitsBlockTheSlot(AppointmentStatus status) {
        db.add(row(1L, otherPatient(), RUIZ, VisitType.BLOOD_TEST, at(MON, 10, 0), 15, status));

        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 10, 0))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Dr. Elena Ruiz already has an appointment from 10:00 to 10:15");
    }

    @Test
    @DisplayName("RULE-020 STORY-001 a CANCELLED visit frees the slot (query excludes CANCELLED)")
    void RULE_020_STORY001_cancelledVisitFreesTheSlot() {
        db.add(row(1L, otherPatient(), RUIZ, VisitType.BLOOD_TEST, at(MON, 10, 0), 15, AppointmentStatus.CANCELLED));

        assertThat(service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 10, 0))).getId()).isEqualTo(100L);
        verify(appointmentRepository).findByClinicianAndStatusNotAndStartTimeBetween(
                eq(RUIZ), eq(AppointmentStatus.CANCELLED), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("RULE-020 STORY-001 when both clinician and patient clash, the clinician conflict is reported")
    void RULE_020_STORY001_clinicianConflictReportedBeforePatientConflict() {
        db.add(row(1L, laura, RUIZ, VisitType.BLOOD_TEST, at(MON, 10, 0), 15, AppointmentStatus.SCHEDULED));

        assertThatThrownBy(() -> service.schedule(request(VisitType.BLOOD_TEST, RUIZ, at(MON, 10, 0))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Dr. Elena Ruiz already has an appointment from 10:00 to 10:15");
    }

    // ---------------------------------------------------------------- RULE-021

    @Test
    @DisplayName("RULE-021 STORY-001 Laura has a Blood test 11:00-11:15 with Ruiz; Follow-up 11:00 with Bianchi -> 409")
    void RULE_021_STORY001_patientCannotHoldTwoOverlappingVisitsWithAnyClinician() {
        db.add(row(1L, laura, RUIZ, VisitType.BLOOD_TEST, at(MON, 11, 0), 15, AppointmentStatus.SCHEDULED));

        assertThatThrownBy(() -> service.schedule(request(VisitType.FOLLOW_UP, BIANCHI, at(MON, 11, 0))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Patient Laura Gomez already has an appointment from 11:00 to 11:15");
        verify(appointmentRepository).findByPatientIdAndStatusNotAndStartTimeBetween(
                7L, AppointmentStatus.CANCELLED, at(MON, 10, 0), at(MON, 11, 30));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("RULE-021 STORY-001 back-to-back for the same patient (11:15 after 11:00-11:15) is accepted")
    void RULE_021_STORY001_patientBackToBackIsAccepted() {
        db.add(row(1L, laura, RUIZ, VisitType.BLOOD_TEST, at(MON, 11, 0), 15, AppointmentStatus.SCHEDULED));

        assertThat(service.schedule(request(VisitType.FOLLOW_UP, BIANCHI, at(MON, 11, 15))).getId()).isEqualTo(100L);
    }

    // ---------------------------------------------------------------- RULE-033

    @Test
    @DisplayName("RULE-033 STORY-001 SCHEDULED -> NO_SHOW is saved; a later change -> 409 already NO_SHOW")
    void RULE_033_STORY001_terminalStateCannotBeChangedAgain() {
        Appointment a = row(12L, laura, RUIZ, VisitType.BLOOD_TEST, at(MON, 9, 0), 15, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findById(12L)).thenReturn(Optional.of(a));

        assertThat(service.updateStatus(12L, AppointmentStatus.NO_SHOW).getStatus())
                .isEqualTo(AppointmentStatus.NO_SHOW);
        assertThatThrownBy(() -> service.updateStatus(12L, AppointmentStatus.COMPLETED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment is already NO_SHOW and can no longer be changed");
    }

    @ParameterizedTest(name = "RULE-033 STORY-001 a {0} appointment is final (409)")
    @EnumSource(value = AppointmentStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    void RULE_033_STORY001_everyTerminalStateIsFinal(AppointmentStatus current) {
        Appointment a = row(12L, laura, RUIZ, VisitType.BLOOD_TEST, at(MON, 9, 0), 15, current);
        when(appointmentRepository.findById(12L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.updateStatus(12L, AppointmentStatus.CANCELLED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment is already " + current + " and can no longer be changed");
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("RULE-033 STORY-001 target SCHEDULED -> 400 Status can only be changed to COMPLETED, CANCELLED or NO_SHOW")
    void RULE_033_STORY001_movingToScheduledIsRefused() {
        Appointment a = row(12L, laura, RUIZ, VisitType.BLOOD_TEST, at(MON, 9, 0), 15, AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findById(12L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.updateStatus(12L, AppointmentStatus.SCHEDULED))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Status can only be changed to COMPLETED, CANCELLED or NO_SHOW");
    }

    @Test
    @DisplayName("RULE-033 STORY-001 the SCHEDULED-target check runs before the terminal check (CANCELLED -> SCHEDULED is 400, not 409)")
    void RULE_033_STORY001_scheduledTargetCheckedBeforeTerminalCheck() {
        Appointment a = row(12L, laura, RUIZ, VisitType.BLOOD_TEST, at(MON, 9, 0), 15, AppointmentStatus.CANCELLED);
        when(appointmentRepository.findById(12L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.updateStatus(12L, AppointmentStatus.SCHEDULED))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Status can only be changed to COMPLETED, CANCELLED or NO_SHOW");
    }

    @Test
    @DisplayName("RULE-033 STORY-001 unknown appointment 99 -> 404 Appointment 99 not found")
    void RULE_033_STORY001_unknownAppointmentIsNotFound() {
        when(appointmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, AppointmentStatus.COMPLETED))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Appointment 99 not found");
    }

    @Test
    @DisplayName("RULE-033 STORY-001 no time check: a visit on 2026-11-20 can be marked COMPLETED today")
    void RULE_033_STORY001_futureVisitCanBeCompletedEarly() {
        Appointment a = row(12L, laura, RUIZ, VisitType.BLOOD_TEST, at(LocalDate.of(2026, 11, 20), 9, 0), 15,
                AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findById(12L)).thenReturn(Optional.of(a));

        assertThat(service.updateStatus(12L, AppointmentStatus.COMPLETED).getStatus())
                .isEqualTo(AppointmentStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- RULE-034

    @Test
    @DisplayName("RULE-034 STORY-001 another patient's appointment -> 404 Appointment 1 not found, nothing saved")
    void RULE_034_STORY001_someoneElsesAppointmentLooksNotFound() {
        Appointment a = row(1L, otherPatient(), RUIZ, VisitType.BLOOD_TEST, at(MON, 9, 0), 15,
                AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancelForPatient(1L, 7L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Appointment 1 not found");
        assertThat(a.getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    @DisplayName("RULE-034 STORY-001 the patient's own COMPLETED visit -> 409 already COMPLETED")
    void RULE_034_STORY001_ownTerminalVisitFollowsStateMachine() {
        Appointment a = row(1L, laura, RUIZ, VisitType.BLOOD_TEST, at(MON, 9, 0), 15, AppointmentStatus.COMPLETED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.cancelForPatient(1L, 7L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Appointment is already COMPLETED and can no longer be changed");
    }

    @Test
    @DisplayName("RULE-034 STORY-001 no notice cutoff: the patient can cancel a SCHEDULED visit that is already in the past")
    void RULE_034_STORY001_pastScheduledVisitCanBeCancelled() {
        Appointment a = row(1L, laura, RUIZ, VisitType.BLOOD_TEST, at(LocalDate.of(2026, 10, 20), 9, 0), 15,
                AppointmentStatus.SCHEDULED);
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(a));

        assertThat(service.cancelForPatient(1L, 7L).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("RULE-034 STORY-001 unknown appointment 5 -> 404 Appointment 5 not found")
    void RULE_034_STORY001_unknownAppointmentIsNotFound() {
        when(appointmentRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelForPatient(5L, 7L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Appointment 5 not found");
    }
}
