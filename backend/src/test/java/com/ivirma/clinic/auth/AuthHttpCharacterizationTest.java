package com.ivirma.clinic.auth;

import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import com.ivirma.clinic.patient.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-001 characterization of the auth endpoints over HTTP with the REAL {@link AuthService},
 * {@link TokenService}, {@link SecurityConfig} and its BCrypt encoder (only repositories and
 * PatientService are mocked): RULE-028, DELTA-013 (question Q16), RULE-040/041/045 at the HTTP layer.
 *
 * <p>All passwords here are clearly-fake test values of the needed length, not legacy defaults.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, TokenService.class, AuthService.class})
@DisplayName("STORY-001 auth endpoints characterization (real AuthService + BCrypt)")
class AuthHttpCharacterizationTest {

    /** 80 ASCII characters: 72 that BCrypt uses, then 8 that legacy BCrypt silently ignores. */
    private static final String PASSWORD_80 =
            "fake-long-pass-0123456789-abcdefghijklmnopqrstuvwxyz-ABCDEFGHIJKLMNOPQRS" + "TUVWXYZ!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private PatientRepository patientRepository;

    @MockBean
    private PatientService patientService;

    @BeforeEach
    void setUp() {
        Patient patient = new Patient();
        patient.setId(42L);
        patient.setFirstName("Ana");
        patient.setLastName("Lopez");
        when(patientService.register(any())).thenReturn(patient);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });
    }

    private static String signupBody(String password) {
        return "{\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"dateOfBirth\":\"1990-01-01\","
                + "\"email\":\"ana.lopez@example.com\",\"treatmentInterest\":\"IVF\""
                + (password == null ? "" : ",\"password\":\"" + password + "\"") + "}";
    }

    private static String repeat(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) {
            sb.append("fake-pw-");
        }
        return sb.substring(0, length);
    }

    // ---------------------------------------------------------------- RULE-028

    @Test
    @DisplayName("RULE-028 STORY-001 a 7-character password -> 400 password: size must be between 8 and 100")
    void RULE_028_STORY001_sevenCharactersIsTooShort() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(signupBody(repeat(7))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password: size must be between 8 and 100"));
        verify(patientService, never()).register(any());
    }

    @Test
    @DisplayName("RULE-028 STORY-001 an 8-character password -> 201")
    void RULE_028_STORY001_eightCharactersIsAccepted() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(signupBody(repeat(8))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RULE-028 STORY-001 a 100-character password -> 201")
    void RULE_028_STORY001_hundredCharactersIsAccepted() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(signupBody(repeat(100))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RULE-028 STORY-001 a 101-character password -> 400 password: size must be between 8 and 100")
    void RULE_028_STORY001_hundredAndOneCharactersIsTooLong() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(signupBody(repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password: size must be between 8 and 100"));
    }

    @Test
    @DisplayName("RULE-028 STORY-001 a missing password -> 400 password: must not be blank")
    void RULE_028_STORY001_missingPasswordIsRefused() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(signupBody(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("password: must not be blank"));
    }

    // ---------------------------------------------------------------- RULE-027 / RULE-035 over HTTP

    /**
     * BUSINESS_RULES.md RULE-035's example signs up " Ana.Lopez@Example.com " (padded) and expects 201. Over HTTP the
     * legacy answers 400: {@code @Email} on PatientRequest rejects the padding before AuthService trims it. Mixed
     * case alone is accepted and lowercased.
     */
    @Test
    @DisplayName("RULE-035 RULE-027 STORY-001 signup email with surrounding spaces -> 400 email: must be a well-formed email address")
    void RULE_035_RULE_027_STORY001_paddedEmailRejectedBeforeNormalisation() throws Exception {
        String body = signupBody(repeat(12)).replace("\"ana.lopez@example.com\"", "\" Ana.Lopez@Example.com \"");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));
        verify(patientService, never()).register(any());
    }

    @Test
    @DisplayName("RULE-035 STORY-001 signup with a mixed-case email -> 201, username lowercased")
    void RULE_035_STORY001_mixedCaseEmailIsLowercasedOverHttp() throws Exception {
        String body = signupBody(repeat(12)).replace("\"ana.lopez@example.com\"", "\"Ana.Lopez@Example.COM\"");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("ana.lopez@example.com"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.patientId").value(42));
    }

    // ---------------------------------------------------------------- DELTA-013 / Q16

    /**
     * DELTA-013, open decision Q16. Legacy (Spring Security 6.2) BCrypt silently uses only the first
     * 72 bytes, so an 80-character signup is accepted (201) and any password sharing those 72 bytes
     * signs in. Security 6.3+ throws for over-72-byte input (201 becomes 500). When Q16 is decided,
     * update THIS test deliberately; until then it pins the legacy behaviour.
     */
    @Test
    @DisplayName("DELTA-013 Q16 STORY-001 80-char password -> 201 today; BCrypt truncates to 72 bytes (pending decision Q16)")
    void DELTA_013_Q16_STORY001_bcryptSilentlyTruncatesPasswordsOver72Bytes() throws Exception {
        assertThat(PASSWORD_80).hasSize(80).matches("\\p{ASCII}+");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(signupBody(PASSWORD_80)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("ana.lopez@example.com"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        String hash = captor.getValue().getPasswordHash();
        String prefix72 = PASSWORD_80.substring(0, 72);

        assertThat(passwordEncoder.matches(PASSWORD_80, hash)).isTrue();
        assertThat(passwordEncoder.matches(prefix72, hash)).isTrue();
        assertThat(passwordEncoder.matches(prefix72 + "DIFFERENT-TAIL", hash)).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD_80.substring(0, 71), hash)).isFalse();
    }

    // ---------------------------------------------------------------- RULE-040 / RULE-041 over HTTP

    @Test
    @DisplayName("RULE-041 STORY-001 POST /api/auth/logout with T1 -> 204; T1 then gets 401, T2 still 200")
    void RULE_041_STORY001_logoutRevokesOnlyThePresentedTokenOverHttp() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(5L, "ana.lopez@example.com", Role.CUSTOMER, "Ana Lopez", 42L);
        String t1 = tokenService.issue(user);
        String t2 = tokenService.issue(user);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + t1))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please sign in"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ana.lopez@example.com"));
    }

    @Test
    @DisplayName("RULE-041 STORY-001 POST /api/auth/logout without a token -> 401 (logout needs a signed-in user)")
    void RULE_041_STORY001_logoutRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please sign in"));
    }

    @Test
    @DisplayName("RULE-040 STORY-001 the header prefix is case-sensitive: 'bearer <token>' is anonymous (401)")
    void RULE_040_STORY001_lowercaseBearerPrefixIsIgnored() throws Exception {
        String token = tokenService.issue(new AuthenticatedUser(1L, "admin", Role.ADMIN, "Clinic coordinator", null));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RULE-040 STORY-001 whitespace around the token after 'Bearer ' is trimmed")
    void RULE_040_STORY001_tokenIsTrimmedAfterPrefix() throws Exception {
        String token = tokenService.issue(new AuthenticatedUser(1L, "admin", Role.ADMIN, "Clinic coordinator", null));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer   " + token + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // ---------------------------------------------------------------- RULE-045 over HTTP

    @Test
    @DisplayName("RULE-045 STORY-001 unknown user and wrong password both -> 401 Invalid email/username or password")
    void RULE_045_STORY001_uniformLoginErrorOverHttp() throws Exception {
        UserAccount laura = new UserAccount();
        laura.setId(5L);
        laura.setUsername("laura.gomez@example.com");
        laura.setPasswordHash(passwordEncoder.encode("fake-right-pass"));
        laura.setRole(Role.CUSTOMER);
        laura.setDisplayName("Laura Gomez");
        when(userAccountRepository.findByUsernameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userAccountRepository.findByUsernameIgnoreCase("Laura.Gomez@Example.com")).thenReturn(Optional.of(laura));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody@example.com\",\"password\":\"fake-any-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email/username or password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" Laura.Gomez@Example.com \",\"password\":\"fake-wrong-pass\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email/username or password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" Laura.Gomez@Example.com \",\"password\":\"fake-right-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("laura.gomez@example.com"));
    }
}
