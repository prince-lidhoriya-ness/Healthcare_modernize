package com.ivirma.clinic.characterization;

import com.ivirma.clinic.appointment.Appointment;
import com.ivirma.clinic.appointment.AppointmentRepository;
import com.ivirma.clinic.appointment.AppointmentService;
import com.ivirma.clinic.auth.AuthService;
import com.ivirma.clinic.auth.AuthenticatedUser;
import com.ivirma.clinic.auth.Role;
import com.ivirma.clinic.auth.SecurityConfig;
import com.ivirma.clinic.auth.TokenService;
import com.ivirma.clinic.auth.UserAccount;
import com.ivirma.clinic.auth.UserAccountRepository;
import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.dashboard.DashboardService;
import com.ivirma.clinic.doctor.Doctor;
import com.ivirma.clinic.doctor.DoctorRepository;
import com.ivirma.clinic.doctor.DoctorService;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import com.ivirma.clinic.patient.PatientSearchRepository;
import com.ivirma.clinic.patient.PatientService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * TASK-001 (STORY-001): golden API responses for every endpoint in {@code docs/api.md}.
 *
 * <p>HOW THESE WERE RECORDED: through MockMvc against the real web layer (all controllers, the real
 * SecurityConfig/TokenService/GlobalExceptionHandler and Jackson configuration) and the REAL services
 * (AuthService, PatientService, AppointmentService, DoctorService, DashboardService). Only the
 * persistence boundary (the five repositories) is replaced by seeded, literal fixtures, and
 * {@code LocalDateTime.now()} is frozen at {@link Fixtures#NOW}. A live-stack recording (backend +
 * PostgreSQL) was not used for the golden files because ids, createdAt values and "now"-relative
 * appointment windows would make them non-reproducible; the DB-backed paths are pinned separately by
 * {@code PersistenceIntegrationTest} and {@code FullStackIntegrationTest}.
 *
 * <p>Normalised before comparison (each value's FORMAT is asserted first): {@code token} (43-char
 * URL-safe base64), error {@code timestamp} (Jackson 2 {@code yyyy-MM-dd'T'HH:mm:ss.SSS+hh:mm}),
 * dashboard {@code generatedAt} ({@code dd/MM/yyyy HH:mm}). Ids are literal fixture ids.
 *
 * <p>Files live in {@code src/test/resources/golden/<case>.txt}. A missing file is a FAILURE, not a
 * skip. Re-record (only on the legacy stack, and review the diff) with {@code -Dgolden.record=true}.
 */
@WebMvcTest
@Import({SecurityConfig.class, TokenService.class, AuthService.class, PatientService.class,
        AppointmentService.class, DoctorService.class, DashboardService.class})
@DisplayName("TASK-001 STORY-001 golden API responses")
class GoldenApiResponsesTest {

    private static final boolean RECORD = Boolean.getBoolean("golden.record");
    private static final Path GOLDEN_SOURCE_DIR = Paths.get("src", "test", "resources", "golden");
    private static final AtomicInteger EXECUTED = new AtomicInteger();

    /** Clearly-fake passwords for the fixture accounts (not the legacy demo defaults). */
    static final String FAKE_ADMIN_PASSWORD = "fixture-admin-pass";
    static final String FAKE_CUSTOMER_PASSWORD = "fixture-customer-pass";

    private static final String TOKEN_FORMAT = "[A-Za-z0-9_-]{43}";
    private static final String TIMESTAMP_FORMAT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}";
    private static final String GENERATED_AT_FORMAT = "\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    PatientRepository patientRepository;

    @MockBean
    PatientSearchRepository patientSearchRepository;

    @MockBean
    AppointmentRepository appointmentRepository;

    @MockBean
    DoctorRepository doctorRepository;

    @MockBean
    UserAccountRepository userAccountRepository;

    private MockedStatic<LocalDateTime> clock;
    private final Map<Long, Appointment> appointments = new HashMap<Long, Appointment>();
    private String adminToken;
    private String customerToken;

    // ------------------------------------------------------------------ cases

    record GoldenCase(String id, String description, String method, String path, String who, String body,
                      Consumer<GoldenApiResponsesTest> arrange) {
        @Override
        public String toString() {
            return id + ": " + description;
        }
    }

    private static GoldenCase c(String id, String description, String method, String path, String who, String body) {
        return new GoldenCase(id, description, method, path, who, body, t -> { });
    }

    private static GoldenCase c(String id, String description, String method, String path, String who, String body,
                                Consumer<GoldenApiResponsesTest> arrange) {
        return new GoldenCase(id, description, method, path, who, body, arrange);
    }

    private static final String NEW_PATIENT = "{\"firstName\":\"Testpatient\",\"lastName\":\"Example\","
            + "\"dateOfBirth\":\"1990-06-15\",\"email\":\"test.example@example.com\",\"phone\":\"+34 600 000 999\","
            + "\"treatmentInterest\":\"EGG_FREEZING\"}";

    static Stream<GoldenCase> cases() {
        String under18 = LocalDate.now().minusYears(17).toString();
        return Stream.of(
                c("auth_login_admin", "RULE-045 admin signs in on the admin portal", "POST", "/api/auth/login", "ANON",
                        "{\"username\":\"admin\",\"password\":\"" + FAKE_ADMIN_PASSWORD + "\",\"portal\":\"ADMIN\"}"),
                c("auth_login_wrong_password", "RULE-045 wrong password -> uniform 401", "POST", "/api/auth/login", "ANON",
                        "{\"username\":\"admin\",\"password\":\"fixture-wrong-pass\"}"),
                c("auth_login_customer_on_admin_portal", "RULE-046 customer on the admin portal -> 401", "POST",
                        "/api/auth/login", "ANON", "{\"username\":\"Laura.Gomez@Example.com\",\"password\":\""
                                + FAKE_CUSTOMER_PASSWORD + "\",\"portal\":\"ADMIN\"}"),
                c("auth_register", "RULE-035 customer self-signup -> 201", "POST", "/api/auth/register", "ANON",
                        "{\"firstName\":\"ana\",\"lastName\":\"lopez\",\"dateOfBirth\":\"1990-01-01\","
                                + "\"email\":\"Ana.Lopez@Example.com\",\"treatmentInterest\":\"IVF\","
                                + "\"password\":\"fixture-new-pass\"}"),
                c("auth_register_padded_email", "RULE-035 RULE-027 email with surrounding spaces -> 400 from @Email",
                        "POST", "/api/auth/register", "ANON",
                        "{\"firstName\":\"ana\",\"lastName\":\"lopez\",\"dateOfBirth\":\"1990-01-01\","
                                + "\"email\":\" Ana.Lopez@Example.com \",\"treatmentInterest\":\"IVF\","
                                + "\"password\":\"fixture-new-pass\"}"),
                c("auth_register_existing_patient", "RULE-027 signup with a patient email -> 409", "POST",
                        "/api/auth/register", "ANON", "{\"firstName\":\"Sofia\",\"lastName\":\"Romano\","
                                + "\"dateOfBirth\":\"1992-09-03\",\"email\":\"sofia.romano@example.com\","
                                + "\"treatmentInterest\":\"IVF\",\"password\":\"fixture-new-pass\"}"),
                c("auth_me_customer", "signed-in customer", "GET", "/api/auth/me", "CUSTOMER", null),
                c("auth_logout", "RULE-041 logout -> 204", "POST", "/api/auth/logout", "ADMIN", null),
                c("customer_profile", "RULE-047 own patient record", "GET", "/api/customer/profile", "CUSTOMER", null),
                c("customer_appointments", "RULE-047 own appointments, newest first", "GET", "/api/customer/appointments",
                        "CUSTOMER", null),
                c("customer_book", "RULE-047 customer books for herself -> 201", "POST", "/api/customer/appointments",
                        "CUSTOMER", "{\"patientId\":2,\"visitType\":\"BLOOD_TEST\",\"clinician\":\"Dr. Priya Nair\","
                                + "\"startTime\":\"2026-11-03T09:30\"}"),
                c("customer_cancel_own", "RULE-034 cancel own scheduled visit", "PATCH",
                        "/api/customer/appointments/12/cancel", "CUSTOMER", null),
                c("customer_cancel_other", "RULE-034 someone else's visit -> 404", "PATCH",
                        "/api/customer/appointments/13/cancel", "CUSTOMER", null),
                c("reference_data", "RULE-001 reference data (open)", "GET", "/api/reference-data", "ANON", null),
                c("doctors_list", "doctors sorted by last name", "GET", "/api/doctors", "ADMIN", null),
                c("doctors_register", "register a doctor -> 201", "POST", "/api/doctors", "ADMIN",
                        "{\"firstName\":\" Ana \",\"lastName\":\"Lopez\",\"specialty\":\"EMBRYOLOGY\","
                                + "\"email\":\"Ana.Lopez@Clinic.example.com\",\"phone\":\"+34 600 000 100\"}"),
                c("doctors_register_duplicate", "RULE-029 duplicate doctor name ignoring case -> 409", "POST",
                        "/api/doctors", "ADMIN", "{\"firstName\":\"elena\",\"lastName\":\"ruiz\",\"specialty\":\"GYNAECOLOGY\","
                                + "\"email\":\"elena.ruiz2@clinic.example.com\"}",
                        t -> when(t.doctorRepository.existsByNameIgnoreCase("Dr. elena ruiz")).thenReturn(true)),
                c("patients_list", "all patients by last name", "GET", "/api/patients", "ADMIN", null),
                c("patients_search", "RULE-009 search 'gom'", "GET", "/api/patients?search=gom", "ADMIN", null),
                c("patients_register", "RULE-010 register a patient -> 201", "POST", "/api/patients", "ADMIN",
                        NEW_PATIENT),
                c("patients_register_trailing_tokens", "DELTA-011 body followed by '{}' -> 201", "POST", "/api/patients",
                        "ADMIN", NEW_PATIENT + " {}"),
                c("patients_register_under_18", "RULE-022 17-year-old -> 400", "POST", "/api/patients", "ADMIN",
                        "{\"firstName\":\"Young\",\"lastName\":\"Patient\",\"dateOfBirth\":\"" + under18 + "\","
                                + "\"email\":\"young@example.com\",\"treatmentInterest\":\"IVF\"}"),
                c("patients_register_duplicate_email", "RULE-024 duplicate email -> 409", "POST", "/api/patients", "ADMIN",
                        "{\"firstName\":\"Sofia\",\"lastName\":\"Romano\",\"dateOfBirth\":\"1992-09-03\","
                                + "\"email\":\"sofia.romano@example.com\",\"treatmentInterest\":\"IVF\"}"),
                c("patients_get", "one patient", "GET", "/api/patients/1", "ADMIN", null),
                c("patients_get_unknown", "unknown patient -> 404", "GET", "/api/patients/99", "ADMIN", null),
                c("patients_update", "RULE-025 update stores fields as sent", "PUT", "/api/patients/2", "ADMIN",
                        "{\"firstName\":\"sofia\",\"lastName\":\"romano \",\"dateOfBirth\":\"2015-01-01\","
                                + "\"email\":\"Sofia.New@Example.com\",\"treatmentInterest\":\"IUI\"}"),
                c("patients_appointments", "patient's appointments, newest first", "GET", "/api/patients/1/appointments",
                        "ADMIN", null),
                c("appointments_book", "RULE-002 book a visit -> 201", "POST", "/api/appointments", "ADMIN",
                        "{\"patientId\":2,\"visitType\":\"BLOOD_TEST\",\"clinician\":\"Dr. Priya Nair\","
                                + "\"startTime\":\"2026-11-02T09:30:45\"}"),
                c("appointments_book_clinician_conflict", "RULE-020 clinician double-booking -> 409", "POST",
                        "/api/appointments", "ADMIN", "{\"patientId\":2,\"visitType\":\"BLOOD_TEST\","
                                + "\"clinician\":\"Dr. Priya Nair\",\"startTime\":\"2026-11-02T09:30\"}",
                        t -> {
                            // build the row first: no LocalDateTime calls may happen inside when(...)
                            List<Appointment> existing = List.of(Fixtures.appointment(15L, Fixtures.laura(),
                                    VisitType.BLOOD_TEST, "Dr. Priya Nair", LocalDateTime.of(2026, 11, 2, 9, 30),
                                    AppointmentStatus.SCHEDULED));
                            when(t.appointmentRepository.findByClinicianAndStatusNotAndStartTimeBetween(
                                    eq("Dr. Priya Nair"), eq(AppointmentStatus.CANCELLED), any(LocalDateTime.class),
                                    any(LocalDateTime.class))).thenReturn(existing);
                        }),
                c("appointments_book_past", "RULE-017 past start -> 400", "POST", "/api/appointments", "ADMIN",
                        "{\"patientId\":2,\"visitType\":\"BLOOD_TEST\",\"clinician\":\"Dr. Priya Nair\","
                                + "\"startTime\":\"2026-10-28T09:30\"}"),
                c("appointments_book_sunday", "RULE-018 Sunday -> 400", "POST", "/api/appointments", "ADMIN",
                        "{\"patientId\":2,\"visitType\":\"BLOOD_TEST\",\"clinician\":\"Dr. Priya Nair\","
                                + "\"startTime\":\"2026-11-01T10:00\"}"),
                c("appointments_book_after_hours", "RULE-019 ends after 19:00 -> 400", "POST", "/api/appointments",
                        "ADMIN", "{\"patientId\":2,\"visitType\":\"INITIAL_CONSULTATION\",\"clinician\":\"Dr. Priya Nair\","
                                + "\"startTime\":\"2026-11-02T18:30\"}"),
                c("appointments_book_unknown_clinician", "RULE-016 wrong-case clinician -> 400", "POST",
                        "/api/appointments", "ADMIN", "{\"patientId\":2,\"visitType\":\"BLOOD_TEST\","
                                + "\"clinician\":\"dr. priya nair\",\"startTime\":\"2026-11-02T09:30\"}"),
                c("appointments_book_missing_clinician", "RULE-014 missing clinician -> 400", "POST",
                        "/api/appointments", "ADMIN", "{\"patientId\":2,\"visitType\":\"BLOOD_TEST\","
                                + "\"startTime\":\"2026-11-02T09:30\"}"),
                c("appointments_upcoming", "RULE-008 upcoming (default)", "GET", "/api/appointments", "ADMIN", null),
                c("appointments_all", "RULE-008 all, newest first", "GET", "/api/appointments?upcoming=false", "ADMIN",
                        null),
                c("appointments_status_completed", "RULE-033 SCHEDULED -> COMPLETED", "PATCH",
                        "/api/appointments/12/status", "ADMIN", "{\"status\":\"COMPLETED\"}"),
                c("appointments_status_final", "RULE-033 COMPLETED is final -> 409", "PATCH",
                        "/api/appointments/11/status", "ADMIN", "{\"status\":\"CANCELLED\"}"),
                c("appointments_status_to_scheduled", "RULE-033 target SCHEDULED -> 400", "PATCH",
                        "/api/appointments/12/status", "ADMIN", "{\"status\":\"SCHEDULED\"}"),
                c("dashboard_summary", "RULE-004..007 dashboard", "GET", "/api/dashboard/summary", "ADMIN", null),
                c("security_anonymous_admin_api", "RULE-044 no token -> 401", "GET", "/api/patients", "ANON", null),
                c("security_customer_on_admin_api", "RULE-044 customer on admin API -> 403", "GET", "/api/patients",
                        "CUSTOMER", null),
                c("security_admin_on_customer_api", "RULE-044 admin on customer API -> 403", "GET",
                        "/api/customer/appointments", "ADMIN", null));
    }

    // ------------------------------------------------------------------ fixtures

    @BeforeEach
    void seed() {
        // initialise Fixtures (whose constants call LocalDateTime.of) BEFORE LocalDateTime is statically mocked
        LocalDateTime frozenNow = Fixtures.NOW;
        clock = mockStatic(LocalDateTime.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS).strictness(Strictness.LENIENT));
        clock.when(LocalDateTime::now).thenReturn(frozenNow);

        Patient laura = Fixtures.laura();
        Patient sofia = Fixtures.sofia();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(laura));
        when(patientRepository.findById(2L)).thenReturn(Optional.of(sofia));
        when(patientRepository.existsById(1L)).thenReturn(true);
        when(patientRepository.existsById(2L)).thenReturn(true);
        when(patientRepository.count()).thenReturn(2L);
        when(patientRepository.countByCreatedAtAfter(any(LocalDateTime.class))).thenReturn(1L);
        when(patientRepository.findAllByOrderByLastNameAscFirstNameAsc()).thenReturn(List.of(laura, sofia));
        when(patientRepository.existsByEmailIgnoreCase(anyString())).thenAnswer(inv ->
                "sofia.romano@example.com".equalsIgnoreCase(inv.getArgument(0))
                        || "laura.gomez@example.com".equalsIgnoreCase(inv.getArgument(0)));
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(3L);
            }
            return p;
        });
        when(patientSearchRepository.search("gom")).thenReturn(List.of(laura));

        List<Doctor> doctors = Fixtures.doctors();
        when(doctorRepository.findAllByOrderByLastNameAscFirstNameAsc()).thenReturn(doctors);
        when(doctorRepository.existsByName(anyString())).thenAnswer(inv ->
                doctors.stream().anyMatch(d -> d.getName().equals(inv.getArgument(0))));
        when(doctorRepository.save(any(Doctor.class))).thenAnswer(inv -> {
            Doctor d = inv.getArgument(0);
            d.setId(5L);
            return d;
        });

        appointments.clear();
        put(Fixtures.appointment(11L, laura, VisitType.INITIAL_CONSULTATION, "Dr. Elena Ruiz",
                LocalDateTime.of(2026, 10, 20, 9, 0), AppointmentStatus.COMPLETED));
        put(Fixtures.appointment(12L, laura, VisitType.EGG_RETRIEVAL, "Dr. Elena Ruiz",
                LocalDateTime.of(2026, 10, 30, 9, 0), AppointmentStatus.SCHEDULED));
        put(Fixtures.appointment(13L, sofia, VisitType.ULTRASOUND_MONITORING, "Dr. Marco Bianchi",
                LocalDateTime.of(2026, 10, 30, 10, 30), AppointmentStatus.SCHEDULED));
        put(Fixtures.appointment(14L, sofia, VisitType.FOLLOW_UP, "Dr. Marco Bianchi",
                LocalDateTime.of(2026, 10, 29, 14, 0), AppointmentStatus.SCHEDULED));
        when(appointmentRepository.findById(anyLong())).thenAnswer(inv ->
                Optional.ofNullable(appointments.get(inv.<Long>getArgument(0))));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(21L);
            }
            return a;
        });
        when(appointmentRepository.findByStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                AppointmentStatus.SCHEDULED, Fixtures.NOW)).thenReturn(ids(14, 12, 13));
        when(appointmentRepository.findAllByOrderByStartTimeDesc()).thenReturn(ids(13, 12, 14, 11));
        when(appointmentRepository.findByPatientIdOrderByStartTimeDesc(1L)).thenReturn(ids(12, 11));
        when(appointmentRepository.findByPatientIdOrderByStartTimeDesc(2L)).thenReturn(ids(13, 14));
        when(appointmentRepository.findByStatusAndStartTimeBetweenOrderByStartTimeAsc(
                eq(AppointmentStatus.SCHEDULED), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenAnswer(inv -> Fixtures.NOW.equals(inv.getArgument(1)) ? ids(14, 12, 13) : ids(14));
        when(appointmentRepository.countByStatus()).thenReturn(List.of(
                new Object[]{AppointmentStatus.SCHEDULED, 3L}, new Object[]{AppointmentStatus.COMPLETED, 1L}));
        when(appointmentRepository.countByVisitType()).thenReturn(List.of(
                new Object[]{VisitType.INITIAL_CONSULTATION, 1L}, new Object[]{VisitType.EGG_RETRIEVAL, 1L},
                new Object[]{VisitType.ULTRASOUND_MONITORING, 1L}, new Object[]{VisitType.FOLLOW_UP, 1L}));

        UserAccount admin = account(1L, "admin", FAKE_ADMIN_PASSWORD, Role.ADMIN, "Clinic coordinator", null);
        UserAccount customer = account(2L, "laura.gomez@example.com", FAKE_CUSTOMER_PASSWORD, Role.CUSTOMER,
                "Laura Gomez", laura);
        when(userAccountRepository.findByUsernameIgnoreCase(anyString())).thenAnswer(inv -> {
            String name = inv.getArgument(0);
            if (name.equalsIgnoreCase("admin")) {
                return Optional.of(admin);
            }
            if (name.equalsIgnoreCase("laura.gomez@example.com")) {
                return Optional.of(customer);
            }
            return Optional.empty();
        });
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount a = inv.getArgument(0);
            a.setId(3L);
            return a;
        });

        adminToken = tokenService.issue(AuthenticatedUser.from(admin));
        customerToken = tokenService.issue(AuthenticatedUser.from(customer));
    }

    @AfterEach
    void releaseClock() {
        clock.close();
    }

    @AfterAll
    static void report() {
        System.out.println("TASK-001 golden: equivalence cases executed: " + EXECUTED.get()
                + (RECORD ? " (RECORD MODE: files rewritten, nothing compared)" : ""));
        assertThat(EXECUTED.get()).as("equivalence cases executed").isPositive();
    }

    private void put(Appointment a) {
        appointments.put(a.getId(), a);
    }

    private List<Appointment> ids(long... ids) {
        List<Appointment> list = new ArrayList<Appointment>();
        for (long id : ids) {
            list.add(appointments.get(id));
        }
        return list;
    }

    private UserAccount account(long id, String username, String password, Role role, String displayName,
                                Patient patient) {
        UserAccount a = new UserAccount();
        a.setId(id);
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode(password));
        a.setRole(role);
        a.setDisplayName(displayName);
        a.setPatient(patient);
        a.setCreatedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        return a;
    }

    // ------------------------------------------------------------------ the test

    @ParameterizedTest(name = "TASK-001 STORY-001 {0}")
    @MethodSource("cases")
    void TASK_001_STORY001_goldenResponse(GoldenCase golden) throws Exception {
        golden.arrange().accept(this);

        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(golden.method()), golden.path());
        if ("ADMIN".equals(golden.who())) {
            builder.header("Authorization", "Bearer " + adminToken);
        } else if ("CUSTOMER".equals(golden.who())) {
            builder.header("Authorization", "Bearer " + customerToken);
        }
        if (golden.body() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(golden.body());
        }
        MvcResult result = mockMvc.perform(builder).andReturn();

        String contentType = result.getResponse().getContentType();
        String actual = "status: " + result.getResponse().getStatus() + "\n"
                + "content-type: " + (contentType == null ? "-" : contentType) + "\n"
                + "body: " + normalise(result.getResponse().getContentAsString(StandardCharsets.UTF_8)) + "\n";

        String fileName = golden.id() + ".txt";
        if (RECORD) {
            String header = "# TASK-001 golden response, recorded from the LEGACY stack (Boot 3.2 / Java 21)\n"
                    + "# via MockMvc + seeded fixtures (see GoldenApiResponsesTest), clock frozen at " + Fixtures.NOW + "\n"
                    + "# case: " + golden + "\n"
                    + "# request: " + golden.method() + " " + golden.path() + " as " + golden.who()
                    + (golden.body() == null ? "" : " with body " + golden.body()) + "\n";
            Files.createDirectories(GOLDEN_SOURCE_DIR);
            Files.writeString(GOLDEN_SOURCE_DIR.resolve(fileName), header + actual, StandardCharsets.UTF_8);
        } else {
            assertThat(withoutComments(readGolden(fileName))).as("golden " + fileName).isEqualTo(actual);
        }
        EXECUTED.incrementAndGet();
    }

    private static String readGolden(String fileName) throws IOException {
        try (InputStream in = GoldenApiResponsesTest.class.getResourceAsStream("/golden/" + fileName)) {
            if (in == null) {
                fail("Golden file golden/" + fileName + " is missing. A missing recording is a failure, not a skip: "
                        + "record it on the LEGACY stack with -Dgolden.record=true and review it.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String withoutComments(String content) {
        return content.replace("\r\n", "\n").lines().filter(l -> !l.startsWith("#"))
                .collect(Collectors.joining("\n")) + "\n";
    }

    static String normalise(String body) {
        String out = replaceChecked(body, "\"token\":\"([^\"]*)\"", TOKEN_FORMAT, "\"token\":\"<TOKEN>\"");
        out = replaceChecked(out, "\"timestamp\":\"([^\"]*)\"", TIMESTAMP_FORMAT, "\"timestamp\":\"<TIMESTAMP>\"");
        return replaceChecked(out, "\"generatedAt\":\"([^\"]*)\"", GENERATED_AT_FORMAT, "\"generatedAt\":\"<GENERATED_AT>\"");
    }

    private static String replaceChecked(String body, String regex, String requiredFormat, String replacement) {
        Matcher m = Pattern.compile(regex).matcher(body);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            assertThat(m.group(1)).as("volatile value format for " + regex).matches(requiredFormat);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
