package com.ivirma.clinic.characterization;

import com.ivirma.clinic.appointment.AppointmentResponse;
import com.ivirma.clinic.auth.AuthenticatedUser;
import com.ivirma.clinic.auth.Role;
import com.ivirma.clinic.auth.SecurityConfig;
import com.ivirma.clinic.auth.TokenService;
import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.NotFoundException;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.dashboard.DashboardController;
import com.ivirma.clinic.dashboard.DashboardService;
import com.ivirma.clinic.dashboard.DashboardSummary;
import com.ivirma.clinic.doctor.DoctorController;
import com.ivirma.clinic.doctor.DoctorService;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientController;
import com.ivirma.clinic.patient.PatientRequest;
import com.ivirma.clinic.patient.PatientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * STORY-001 legacy-first characterization of the JSON wire format that Jackson 3 (Boot 4) changes
 * by default: DELTA-009 (property ORDER, Jackson 3 sorts alphabetically), DELTA-010 ({@code java.util.Date}
 * timestamp offset {@code +00:00} becomes {@code Z}) and DELTA-011 (trailing tokens after the JSON
 * body are accepted today). The bodies are compared as exact strings on purpose: jsonPath would not
 * see a reordering. The only normalised value is the error timestamp, whose FORMAT is asserted first.
 */
@WebMvcTest(controllers = {PatientController.class, DoctorController.class, DashboardController.class})
@Import({SecurityConfig.class, TokenService.class})
@DisplayName("STORY-001 JSON wire format characterization (DELTA-009/010/011)")
class JsonWireFormatCharacterizationTest {

    /** Jackson 2 StdDateFormat with a colon in the offset, e.g. 2026-10-29T10:00:00.123+00:00. */
    private static final String LEGACY_TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{2}:\\d{2}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private PatientService patientService;

    @MockBean
    private DoctorService doctorService;

    @MockBean
    private DashboardService dashboardService;

    private String admin() {
        return "Bearer " + tokenService.issue(new AuthenticatedUser(1L, "admin", Role.ADMIN, "Clinic coordinator", null));
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("DELTA-009 STORY-001 GET /api/patients/{id}: fields in declaration order, getter-only fullName LAST")
    void DELTA_009_STORY001_patientJsonFieldOrder() throws Exception {
        when(patientService.get(1L)).thenReturn(Fixtures.laura());

        MvcResult result = mockMvc.perform(get("/api/patients/1").header("Authorization", admin())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).isEqualTo("{\"id\":1,\"firstName\":\"Laura\",\"lastName\":\"Gomez\","
                + "\"dateOfBirth\":\"1989-04-12\",\"email\":\"laura.gomez@example.com\",\"phone\":\"+34 600 000 001\","
                + "\"treatmentInterest\":\"IVF\",\"createdAt\":\"2026-10-01T09:00:00\",\"fullName\":\"Laura Gomez\"}");
    }

    @Test
    @DisplayName("DELTA-009 STORY-001 GET /api/doctors: declaration order, null phone included")
    void DELTA_009_STORY001_doctorListJsonFieldOrder() throws Exception {
        when(doctorService.list()).thenReturn(Fixtures.doctors().subList(0, 2));

        MvcResult result = mockMvc.perform(get("/api/doctors").header("Authorization", admin())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).isEqualTo("["
                + "{\"id\":2,\"name\":\"Dr. Marco Bianchi\",\"firstName\":\"Marco\",\"lastName\":\"Bianchi\","
                + "\"specialty\":\"GYNAECOLOGY\",\"email\":\"marco.bianchi@clinic.example.com\",\"phone\":null,"
                + "\"createdAt\":\"2026-10-01T08:00:00\"},"
                + "{\"id\":3,\"name\":\"Dr. Sarah Collins\",\"firstName\":\"Sarah\",\"lastName\":\"Collins\","
                + "\"specialty\":\"REPRODUCTIVE_MEDICINE\",\"email\":\"sarah.collins@clinic.example.com\",\"phone\":null,"
                + "\"createdAt\":\"2026-10-01T08:00:00\"}]");
    }

    @Test
    @DisplayName("DELTA-009 STORY-001 GET /api/dashboard/summary: summary and nested appointment in declaration order, maps in insertion order")
    void DELTA_009_STORY001_dashboardSummaryJsonFieldOrder() throws Exception {
        Patient sofia = Fixtures.sofia();
        DashboardSummary summary = new DashboardSummary();
        summary.setTotalPatients(2);
        summary.setNewPatientsThisWeek(1);
        summary.setAppointmentsToday(1);
        summary.setUpcomingNext7Days(1);
        Map<String, Long> byStatus = new LinkedHashMap<String, Long>();
        byStatus.put("SCHEDULED", 1L);
        byStatus.put("COMPLETED", 0L);
        byStatus.put("CANCELLED", 0L);
        byStatus.put("NO_SHOW", 0L);
        summary.setAppointmentsByStatus(byStatus);
        Map<String, Long> byType = new LinkedHashMap<String, Long>();
        for (VisitType vt : VisitType.values()) {
            byType.put(vt.getLabel(), vt == VisitType.FOLLOW_UP ? 1L : 0L);
        }
        summary.setAppointmentsByVisitType(byType);
        summary.setNextAppointments(List.of(AppointmentResponse.from(Fixtures.appointment(14L, sofia,
                VisitType.FOLLOW_UP, "Dr. Marco Bianchi", LocalDateTime.of(2026, 10, 29, 14, 0),
                AppointmentStatus.SCHEDULED))));
        summary.setGeneratedAt("29/10/2026 10:00");
        when(dashboardService.getSummary()).thenReturn(summary);

        MvcResult result = mockMvc.perform(get("/api/dashboard/summary").header("Authorization", admin())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(result)).isEqualTo("{\"totalPatients\":2,\"newPatientsThisWeek\":1,\"appointmentsToday\":1,"
                + "\"upcomingNext7Days\":1,"
                + "\"appointmentsByStatus\":{\"SCHEDULED\":1,\"COMPLETED\":0,\"CANCELLED\":0,\"NO_SHOW\":0},"
                + "\"appointmentsByVisitType\":{\"Initial consultation\":0,\"Ultrasound monitoring\":0,\"Blood test\":0,"
                + "\"Egg retrieval\":0,\"Embryo transfer\":0,\"Follow-up\":1},"
                + "\"nextAppointments\":[{\"id\":14,\"patientId\":2,\"patientName\":\"Sofia Romano\","
                + "\"visitType\":\"FOLLOW_UP\",\"visitTypeLabel\":\"Follow-up\",\"clinician\":\"Dr. Marco Bianchi\","
                + "\"startTime\":\"2026-10-29T14:00:00\",\"endTime\":\"2026-10-29T14:30:00\",\"durationMinutes\":30,"
                + "\"status\":\"SCHEDULED\"}],"
                + "\"generatedAt\":\"29/10/2026 10:00\"}");
    }

    @Test
    @DisplayName("DELTA-009 DELTA-010 STORY-001 404 error body: field order status,error,message,detail,timestamp; timestamp ends with +00:00")
    void DELTA_010_STORY001_errorBodyOrderAndTimestampOffsetFormat() throws Exception {
        when(patientService.get(99L)).thenThrow(new NotFoundException("Patient 99 not found"));

        MvcResult result = mockMvc.perform(get("/api/patients/99").header("Authorization", admin())).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(404);
        String body = body(result);
        String timestamp = body.replaceAll(".*\"timestamp\":\"([^\"]*)\".*", "$1");
        assertThat(timestamp).matches(LEGACY_TIMESTAMP).matches(".*[+-]\\d\\d:\\d\\d$").endsWith("+00:00");
        assertThat(body.replace(timestamp, "<TIMESTAMP>")).isEqualTo("{\"status\":404,\"error\":\"Not Found\","
                + "\"message\":\"Patient 99 not found\",\"detail\":null,\"timestamp\":\"<TIMESTAMP>\"}");
    }

    @Test
    @DisplayName("DELTA-011 STORY-001 POST /api/patients with a trailing token body '{...} {}' -> 201 (trailing tokens ignored)")
    void DELTA_011_STORY001_trailingTokensAfterBodyAreIgnored() throws Exception {
        when(patientService.register(any(PatientRequest.class))).thenReturn(Fixtures.laura());
        String json = "{\"firstName\":\"Laura\",\"lastName\":\"Gomez\",\"dateOfBirth\":\"1989-04-12\","
                + "\"email\":\"laura.gomez@example.com\",\"treatmentInterest\":\"IVF\"} {}";

        MvcResult result = mockMvc.perform(post("/api/patients").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("DELTA-011 STORY-001 unknown JSON properties are ignored -> 201")
    void DELTA_011_STORY001_unknownPropertiesAreIgnored() throws Exception {
        when(patientService.register(any(PatientRequest.class))).thenReturn(Fixtures.laura());
        String json = "{\"firstName\":\"Laura\",\"lastName\":\"Gomez\",\"dateOfBirth\":\"1989-04-12\","
                + "\"email\":\"laura.gomez@example.com\",\"treatmentInterest\":\"IVF\",\"favouriteColour\":\"blue\"}";

        MvcResult result = mockMvc.perform(post("/api/patients").header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
    }
}
