package com.ivirma.clinic;

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
import java.util.Map;
import java.util.UUID;

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
@AutoConfigureMockMvc
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
        if (start.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
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
}
