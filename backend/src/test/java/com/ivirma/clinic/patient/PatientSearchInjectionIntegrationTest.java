package com.ivirma.clinic.patient;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-008: patient search must treat the search term as data, never as SQL (SEC-002, CWE-89),
 * while keeping RULE-009 matching and ordering.
 *
 * Runs only when TEST_DB_URL is set (see PersistenceIntegrationTest).
 */
@SpringBootTest(properties = "clinic.seed-demo-data=false")
@AutoConfigureMockMvc(addFilters = false) // search behaviour only; access rules are tested in SecurityRulesTest
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class PatientSearchInjectionIntegrationTest {

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
    @DisplayName("STORY-008 a tautology payload returns no patients instead of every patient")
    void tautologyPayloadMatchesNothing() throws Exception {
        register("Inject", "Tautology");
        assertThat(jdbcTemplate.queryForObject("select count(*) from patient", Long.class)).isPositive();

        mockMvc.perform(get("/api/patients").param("search", "zz-no-such-name%' or 1=1 --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("STORY-008 a UNION payload cannot read the user_account table")
    void unionPayloadReturnsNoRows() throws Exception {
        String payload = "x%' UNION SELECT id, created_at, date_of_birth, username, username, password_hash, "
                + "null, 'IVF' FROM user_account--";

        mockMvc.perform(get("/api/patients").param("search", payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("STORY-008 a single quote is searched literally and does not break the query")
    void singleQuoteIsLiteral() throws Exception {
        String token = uniqueToken();
        register("O'" + token, "Quote");

        mockMvc.perform(get("/api/patients").param("search", "o'" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].lastName").value("Quote"));
    }

    @Test
    @DisplayName("STORY-008 RULE-009 parity: trimmed, case-insensitive substring of last name, ordered by last then first name")
    void ordinarySearchStillMatches() throws Exception {
        String token = uniqueToken();
        register("Zed", token + "beta");
        register("Amy", token + "alpha");

        mockMvc.perform(get("/api/patients").param("search", "  " + token.toUpperCase() + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].firstName").value("Amy"))
                .andExpect(jsonPath("$[1].firstName").value("Zed"));
    }

    private void register(String firstName, String lastName) throws Exception {
        String email = "s8-" + UUID.randomUUID() + "@example.com";
        String json = "{\"firstName\":\"" + firstName.replace("\"", "\\\"") + "\",\"lastName\":\"" + lastName
                + "\",\"dateOfBirth\":\"1990-01-01\",\"email\":\"" + email + "\",\"treatmentInterest\":\"IVF\"}";
        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
    }

    private static String uniqueToken() {
        return "s8" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
