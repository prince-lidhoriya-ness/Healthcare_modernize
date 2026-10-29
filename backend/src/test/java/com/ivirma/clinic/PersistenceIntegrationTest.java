package com.ivirma.clinic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end persistence check against a real PostgreSQL database:
 * register a patient, book an appointment, and read both rows back with SQL.
 *
 * Runs only when TEST_DB_URL is set, e.g.
 *   TEST_DB_URL=jdbc:postgresql://localhost:5432/ivirma_clinic TEST_DB_USER=ivirma_app TEST_DB_PASSWORD=... mvn test
 */
@SpringBootTest(properties = "clinic.seed-demo-data=false")
@AutoConfigureMockMvc(addFilters = false) // persistence only; access rules are tested in SecurityRulesTest
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class PersistenceIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void patientAndAppointmentArePersistedInPostgres() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        String patientJson = "{\"firstName\":\"Integration\",\"lastName\":\"Test\",\"dateOfBirth\":\"1991-02-03\","
                + "\"email\":\"" + email + "\",\"treatmentInterest\":\"IUI\"}";

        MvcResult created = mockMvc.perform(post("/api/patients")
                        .contentType(MediaType.APPLICATION_JSON).content(patientJson))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        Long patientId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        Map<String, Object> patientRow = jdbcTemplate.queryForMap(
                "select first_name, treatment_interest from patient where id = ?", patientId);
        assertThat(patientRow.get("first_name")).isEqualTo("Integration");
        assertThat(patientRow.get("treatment_interest")).isEqualTo("IUI");

        // pick a slot unlikely to clash with other test runs
        LocalDateTime start = TestDates.futureClinicDay(8, 0).plusDays(30 + (int) (patientId % 60));
        if (java.time.DayOfWeek.SUNDAY.equals(start.getDayOfWeek())) {
            start = start.plusDays(1);
        }
        String apptJson = "{\"patientId\":" + patientId + ",\"visitType\":\"BLOOD_TEST\","
                + "\"clinician\":\"Dr. Sarah Collins\",\"startTime\":\"" + start + "\"}";

        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(apptJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"));

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from appointment where patient_id = ? and visit_type = 'BLOOD_TEST'",
                Integer.class, patientId);
        assertThat(count).isEqualTo(1);

        mockMvc.perform(get("/api/patients/" + patientId + "/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].visitTypeLabel").value("Blood test"));
    }

    // ------------------------------------------------------------------ STORY-001 / DELTA-017

    private static String randomLetters(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + ThreadLocalRandom.current().nextInt(26)));
        }
        return sb.toString();
    }

    private Long registerPatient(String firstName, String lastName) throws Exception {
        String json = "{\"firstName\":\"" + firstName + "\",\"lastName\":\"" + lastName + "\","
                + "\"dateOfBirth\":\"1990-03-04\",\"email\":\"it-" + UUID.randomUUID() + "@example.com\","
                + "\"treatmentInterest\":\"IVF\"}";
        String body = mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<String>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            names.add(it.next());
        }
        return names;
    }

    /**
     * DELTA-017: the dashboard's JPQL aggregates ({@code select a.status, count(a) ... group by}) are cast to
     * {@code AppointmentStatus}/{@code Long} in DashboardService; only a real Hibernate run proves the typing.
     */
    @Test
    @DisplayName("DELTA-017 STORY-001 GET /api/dashboard/summary on real PostgreSQL: by-status and by-type maps of numbers, enum order, equal to SQL counts")
    void DELTA_017_STORY001_dashboardSummaryAggregatesOnRealDatabase() throws Exception {
        Long patientId = registerPatient("Dashboard", "Check " + randomLetters(6));
        LocalDateTime start = TestDates.futureClinicDay(12, 0).plusDays(30 + (int) (patientId % 60));
        if (java.time.DayOfWeek.SUNDAY.equals(start.getDayOfWeek())) {
            start = start.plusDays(1);
        }
        mockMvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":" + patientId + ",\"visitType\":\"EMBRYO_TRANSFER\","
                                + "\"clinician\":\"Dr. Priya Nair\",\"startTime\":\"" + start + "\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode summary = objectMapper.readTree(body);

        assertThat(fieldNames(summary)).containsExactly("totalPatients", "newPatientsThisWeek", "appointmentsToday",
                "upcomingNext7Days", "appointmentsByStatus", "appointmentsByVisitType", "nextAppointments", "generatedAt");
        JsonNode byStatus = summary.get("appointmentsByStatus");
        assertThat(fieldNames(byStatus)).containsExactly("SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW");
        for (JsonNode value : byStatus) {
            assertThat(value.isIntegralNumber()).as("appointmentsByStatus value " + value).isTrue();
        }
        JsonNode byType = summary.get("appointmentsByVisitType");
        assertThat(fieldNames(byType)).containsExactly("Initial consultation", "Ultrasound monitoring", "Blood test",
                "Egg retrieval", "Embryo transfer", "Follow-up");
        for (JsonNode value : byType) {
            assertThat(value.isIntegralNumber()).as("appointmentsByVisitType value " + value).isTrue();
        }

        Long scheduledInDb = jdbcTemplate.queryForObject(
                "select count(*) from appointment where status = 'SCHEDULED'", Long.class);
        Long transfersInDb = jdbcTemplate.queryForObject(
                "select count(*) from appointment where visit_type = 'EMBRYO_TRANSFER'", Long.class);
        Long patientsInDb = jdbcTemplate.queryForObject("select count(*) from patient", Long.class);
        assertThat(byStatus.get("SCHEDULED").asLong()).isEqualTo(scheduledInDb).isPositive();
        assertThat(byType.get("Embryo transfer").asLong()).isEqualTo(transfersInDb).isPositive();
        assertThat(summary.get("totalPatients").asLong()).isEqualTo(patientsInDb);
        assertThat(summary.get("generatedAt").asText()).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}");
    }

    /**
     * DELTA-017 / RULE-009: the native search query (string-built SQL, ORDER BY last_name, first_name) on a real DB.
     */
    @Test
    @DisplayName("DELTA-017 STORY-001 GET /api/patients?search=<lastName> on real PostgreSQL: one hit, then two hits ordered by last name")
    void DELTA_017_STORY001_nativePatientSearchOnRealDatabase() throws Exception {
        String token = randomLetters(10);
        Long beta = registerPatient("Zoe", token + " beta");
        Long alpha = registerPatient("Yara", token + " alpha");

        // one hit: the full (title-cased) last name, typed in upper case with surrounding spaces
        String oneHit = mockMvc.perform(get("/api/patients").param("search", " " + (token + " BETA").toUpperCase() + " "))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode one = objectMapper.readTree(oneHit);
        assertThat(one).hasSize(1);
        assertThat(one.get(0).get("id").asLong()).isEqualTo(beta);
        String capitalised = Character.toUpperCase(token.charAt(0)) + token.substring(1);
        assertThat(one.get(0).get("lastName").asText()).isEqualTo(capitalised + " Beta");
        assertThat(fieldNames(one.get(0))).containsExactly("id", "firstName", "lastName", "dateOfBirth", "email",
                "phone", "treatmentInterest", "createdAt", "fullName");

        // two hits: ordered by last name ("... Alpha" before "... Beta"), not by id or first name
        String twoHits = mockMvc.perform(get("/api/patients").param("search", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode two = objectMapper.readTree(twoHits);
        assertThat(two).hasSize(2);
        assertThat(two.get(0).get("id").asLong()).isEqualTo(alpha);
        assertThat(two.get(1).get("id").asLong()).isEqualTo(beta);
    }
}
