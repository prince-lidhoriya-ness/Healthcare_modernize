package com.ivirma.clinic.patient;

import com.ivirma.clinic.testsupport.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-024 (SEC-007): when PostgreSQL rejects a row, its error detail ("Failing row contains (...)")
 * holds the patient's data. No log line, including Hibernate's own SQL error logging, may carry it.
 * The response keeps its legacy status (RULE-025, Q11 strict parity).
 *
 * Runs only when TEST_DB_URL is set (see PersistenceIntegrationTest).
 */
@SpringBootTest(properties = "clinic.seed-demo-data=false")
@AutoConfigureMockMvc(addFilters = false) // logging behaviour only; access rules are tested in SecurityRulesTest
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class PatientConstraintErrorLogPrivacyIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }

    @RegisterExtension
    final LogCapture captured = LogCapture.watching(PatientService.class);

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("STORY-024 a rejected patient row is not echoed into the logs (RULE-025 update with a null name)")
    void rejectedRowIsNotLogged() throws Exception {
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String email = "s24-" + token + "@example.org";
        String body = mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(patientJson("Constraint", "Leak" + token, "1987-06-05", email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String newEmail = "s24-new-" + token + "@example.org";
        mockMvc.perform(put("/api/patients/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content(patientJson(null, "Leak" + token, "1986-04-03", newEmail)))
                .andExpect(status().isInternalServerError());

        captured.assertNoLineCarries(Map.of(
                "last name", "Leak" + token,
                "date of birth", "1986-04-03",
                "email", newEmail));
    }

    private static String patientJson(String firstName, String lastName, String dateOfBirth, String email) {
        String first = firstName == null ? "null" : "\"" + firstName + "\"";
        return "{\"firstName\":" + first + ",\"lastName\":\"" + lastName + "\",\"dateOfBirth\":\"" + dateOfBirth
                + "\",\"email\":\"" + email + "\",\"treatmentInterest\":\"IVF\"}";
    }
}
