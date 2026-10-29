package com.ivirma.clinic.auth;

import com.ivirma.clinic.appointment.AppointmentController;
import com.ivirma.clinic.appointment.AppointmentRequest;
import com.ivirma.clinic.appointment.AppointmentService;
import com.ivirma.clinic.common.ReferenceDataController;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.customer.CustomerPortalController;
import com.ivirma.clinic.dashboard.DashboardController;
import com.ivirma.clinic.dashboard.DashboardService;
import com.ivirma.clinic.doctor.DoctorService;
import com.ivirma.clinic.patient.PatientController;
import com.ivirma.clinic.patient.PatientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-001 characterization of the security layer: RULE-044 (path-to-role matrix and exact
 * 401/403 bodies), RULE-047 (customer portal always uses the session's patient id) and the
 * DELTA-014 path-matcher edges that Security 7's PathPatternRequestMatcher could change.
 * Controllers are real; services are mocks (they return null, so allowed calls give empty 2xx bodies).
 */
@WebMvcTest(controllers = {PatientController.class, AppointmentController.class, DashboardController.class,
        ReferenceDataController.class, CustomerPortalController.class, AuthController.class})
@Import({SecurityConfig.class, TokenService.class})
@DisplayName("STORY-001 security characterization")
class SecurityCharacterizationTest {

    private static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}";
    private static final String SIGNUP = "{\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"dateOfBirth\":\"1990-01-01\","
            + "\"email\":\"ana.lopez@example.com\",\"treatmentInterest\":\"IVF\",\"password\":\"fake-pass-123\"}";
    private static final String LOGIN = "{\"username\":\"admin\",\"password\":\"fake-pass-123\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private PatientService patientService;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private DoctorService doctorService;

    @MockBean
    private AuthService authService;

    private String bearer(String who) {
        switch (who) {
            case "ADMIN":
                return "Bearer " + tokenService.issue(
                        new AuthenticatedUser(1L, "admin", Role.ADMIN, "Clinic coordinator", null));
            case "CUSTOMER":
                return "Bearer " + tokenService.issue(
                        new AuthenticatedUser(2L, "laura.gomez@example.com", Role.CUSTOMER, "Laura Gomez", 42L));
            default:
                return null;
        }
    }

    private MvcResult call(String method, String path, String who, String body) throws Exception {
        MockHttpServletRequestBuilder builder = request(HttpMethod.valueOf(method), path);
        String auth = bearer(who);
        if (auth != null) {
            builder.header("Authorization", auth);
        }
        if (body != null && !body.isEmpty()) {
            builder.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private static String normalisedBody(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String ts = body.replaceAll(".*\"timestamp\":\"([^\"]*)\".*", "$1");
        assertThat(ts).matches(TIMESTAMP);
        return body.replace(ts, "<TIMESTAMP>");
    }

    // ---------------------------------------------------------------- RULE-044

    @ParameterizedTest(name = "RULE-044 STORY-001 {0} {1} as {2} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "GET    | /api/patients               | ANON     | 401",
            "GET    | /api/patients               | CUSTOMER | 403",
            "GET    | /api/patients               | ADMIN    | 200",
            "GET    | /api/appointments           | ANON     | 401",
            "GET    | /api/appointments           | CUSTOMER | 403",
            "GET    | /api/appointments           | ADMIN    | 200",
            "GET    | /api/dashboard/summary      | ANON     | 401",
            "GET    | /api/dashboard/summary      | CUSTOMER | 403",
            "GET    | /api/dashboard/summary      | ADMIN    | 200",
            "GET    | /api/customer/appointments  | ANON     | 401",
            "GET    | /api/customer/appointments  | ADMIN    | 403",
            "GET    | /api/customer/appointments  | CUSTOMER | 200",
            "GET    | /api/customer/profile       | ADMIN    | 403",
            "GET    | /api/auth/me                | ANON     | 401",
            "GET    | /api/auth/me                | ADMIN    | 200",
            "GET    | /api/auth/me                | CUSTOMER | 200",
            "POST   | /api/auth/logout            | ANON     | 401",
            "GET    | /api/reference-data         | ANON     | 200",
            "GET    | /api/reference-data         | CUSTOMER | 200",
            "POST   | /api/reference-data         | ANON     | 401",
            "POST   | /api/reference-data         | CUSTOMER | 403",
            "GET    | /api/auth/login             | ANON     | 401",
            "GET    | /api/auth/register          | ANON     | 401"
    })
    void RULE_044_STORY001_pathToRoleMatrix(String method, String path, String who, int expected) throws Exception {
        assertThat(call(method, path, who, null).getResponse().getStatus()).isEqualTo(expected);
    }

    @Test
    @DisplayName("RULE-044 STORY-001 POST /api/auth/login and /api/auth/register are open to anonymous callers")
    void RULE_044_STORY001_loginAndRegisterAreOpen() throws Exception {
        assertThat(call("POST", "/api/auth/login", "ANON", LOGIN).getResponse().getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/auth/register", "ANON", SIGNUP).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("RULE-044 STORY-001 exact 401 body: Please sign in")
    void RULE_044_STORY001_exactUnauthorizedBody() throws Exception {
        MvcResult result = call("GET", "/api/patients", "ANON", null);

        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        assertThat(normalisedBody(result)).isEqualTo(
                "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Please sign in\",\"detail\":null,"
                        + "\"timestamp\":\"<TIMESTAMP>\"}");
    }

    @Test
    @DisplayName("RULE-044 STORY-001 exact 403 body: Your account does not have access to this portal")
    void RULE_044_STORY001_exactForbiddenBody() throws Exception {
        MvcResult result = call("GET", "/api/dashboard/summary", "CUSTOMER", null);

        assertThat(result.getResponse().getContentType()).isEqualTo("application/json");
        assertThat(normalisedBody(result)).isEqualTo(
                "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Your account does not have access to this portal\","
                        + "\"detail\":null,\"timestamp\":\"<TIMESTAMP>\"}");
    }

    // ---------------------------------------------------------------- RULE-047

    @Test
    @DisplayName("RULE-047 STORY-001 a customer booking uses the session patient (42) even when the body says patientId 999")
    void RULE_047_STORY001_customerBookingIgnoresPatientIdInBody() throws Exception {
        String body = "{\"patientId\":999,\"visitType\":\"BLOOD_TEST\",\"clinician\":\"Dr. Priya Nair\","
                + "\"startTime\":\"2026-11-03T09:30\"}";

        assertThat(call("POST", "/api/customer/appointments", "CUSTOMER", body).getResponse().getStatus())
                .isEqualTo(201);

        ArgumentCaptor<AppointmentRequest> captor = ArgumentCaptor.forClass(AppointmentRequest.class);
        verify(appointmentService).schedule(captor.capture());
        assertThat(captor.getValue().getPatientId()).isEqualTo(42L);
        assertThat(captor.getValue().getVisitType()).isEqualTo(VisitType.BLOOD_TEST);
        assertThat(captor.getValue().getClinician()).isEqualTo("Dr. Priya Nair");
        assertThat(captor.getValue().getStartTime()).isEqualTo(LocalDateTime.of(2026, 11, 3, 9, 30));
    }

    @Test
    @DisplayName("RULE-047 STORY-001 profile, list and cancel all use the session patient id")
    void RULE_047_STORY001_customerReadsAndCancelsUseSessionPatient() throws Exception {
        String customer = bearer("CUSTOMER");

        mockMvc.perform(get("/api/customer/profile").header("Authorization", customer)).andExpect(status().isOk());
        mockMvc.perform(get("/api/customer/appointments").header("Authorization", customer)).andExpect(status().isOk());
        mockMvc.perform(patch("/api/customer/appointments/5/cancel").header("Authorization", customer))
                .andExpect(status().isOk());

        verify(patientService).get(42L);
        verify(appointmentService).listForPatient(42L);
        verify(appointmentService).cancelForPatient(5L, 42L);
    }

    // ---------------------------------------------------------------- DELTA-014

    @Test
    @DisplayName("DELTA-014 STORY-001 OPTIONS preflight on /api/patients (no token) -> 200 with CORS headers")
    void DELTA_014_STORY001_corsPreflightIsAllowedWithoutToken() throws Exception {
        mockMvc.perform(options("/api/patients")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET"))
                .andExpect(header().string("Access-Control-Allow-Headers", "authorization, content-type"));
    }

    @Test
    @DisplayName("DELTA-014 STORY-001 POST /api/auth/register is open (201 without token)")
    void DELTA_014_STORY001_registerIsOpen() throws Exception {
        assertThat(call("POST", "/api/auth/register", "ANON", SIGNUP).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("DELTA-014 STORY-001 GET /api/reference-data is open (200 without token)")
    void DELTA_014_STORY001_referenceDataIsOpen() throws Exception {
        assertThat(call("GET", "/api/reference-data", "ANON", null).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("DELTA-014 STORY-001 GET /actuator/health is not blocked by security (anyRequest permitAll); 200 is pinned in FullStackIntegrationTest")
    void DELTA_014_STORY001_actuatorHealthPassesSecurity() throws Exception {
        int status = call("GET", "/actuator/health", "ANON", null).getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @DisplayName("DELTA-014 STORY-001 /api/patients/ (trailing slash) without token -> 401; with a customer token -> 403")
    void DELTA_014_STORY001_trailingSlashStillProtected() throws Exception {
        assertThat(call("GET", "/api/patients/", "ANON", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call("GET", "/api/patients/", "CUSTOMER", null).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("DELTA-014 STORY-001 /api/customerX is NOT the customer portal: it falls to the admin rule")
    void DELTA_014_STORY001_customerPrefixWithoutSlashFallsToAdminRule() throws Exception {
        assertThat(call("GET", "/api/customerX", "ANON", null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call("GET", "/api/customerX", "CUSTOMER", null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call("GET", "/api/customerX", "ADMIN", null).getResponse().getStatus()).isNotIn(401, 403);
    }
}
