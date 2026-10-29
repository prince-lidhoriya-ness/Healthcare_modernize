package com.ivirma.clinic;

import com.ivirma.clinic.auth.AuthenticatedUser;
import com.ivirma.clinic.auth.Role;
import com.ivirma.clinic.auth.TokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-001 characterization on the full application context (security filters ON, actuator present)
 * against a real PostgreSQL database: the DELTA-014 edges that a {@code @WebMvcTest} slice cannot show.
 *
 * <p>Runs only when TEST_DB_URL is set (same variables as {@link PersistenceIntegrationTest}); the
 * password is read from TEST_DB_PASSWORD, never inlined.
 */
@SpringBootTest(properties = "clinic.seed-demo-data=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@DisplayName("STORY-001 full-stack characterization (real DB, security on)")
class FullStackIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("TEST_DB_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("TEST_DB_PASSWORD"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Test
    @DisplayName("DELTA-014 RULE-044 STORY-001 GET /actuator/health without a token -> 200 status UP (Jenkins Start-app depends on it)")
    void DELTA_014_RULE_044_STORY001_actuatorHealthIsOpen() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    @Test
    @DisplayName("DELTA-014 RULE-044 STORY-001 full stack: /api/patients and /api/patients/ without a token -> 401")
    void DELTA_014_RULE_044_STORY001_adminApiNeedsTokenWithAndWithoutTrailingSlash() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please sign in"));
        mockMvc.perform(get("/api/patients/"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELTA-014 RULE-044 STORY-001 full stack: GET /api/reference-data is open and lists the seeded doctors")
    void DELTA_014_RULE_044_STORY001_referenceDataIsOpen() throws Exception {
        mockMvc.perform(get("/api/reference-data"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitTypes.length()").value(6))
                .andExpect(jsonPath("$.clinicians").isArray());
    }

    @Test
    @DisplayName("DELTA-014 RULE-044 STORY-001 full stack: /api/customerX falls to the admin rule (customer 403, admin passes security)")
    void DELTA_014_RULE_044_STORY001_customerXFallsToAdminRule() throws Exception {
        String customer = "Bearer " + tokenService.issue(
                new AuthenticatedUser(900L, "fixture.customer@example.com", Role.CUSTOMER, "Fixture Customer", 900L));
        String admin = "Bearer " + tokenService.issue(
                new AuthenticatedUser(901L, "fixture-admin", Role.ADMIN, "Fixture Admin", null));

        mockMvc.perform(get("/api/customerX").header("Authorization", customer))
                .andExpect(status().isForbidden());
        int adminStatus = mockMvc.perform(get("/api/customerX").header("Authorization", admin))
                .andReturn().getResponse().getStatus();
        assertThat(adminStatus).isNotIn(401, 403);
    }
}
