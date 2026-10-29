package com.ivirma.clinic.auth;

import com.ivirma.clinic.common.BadRequestException;
import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.TreatmentInterest;
import com.ivirma.clinic.common.UnauthorizedException;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import com.ivirma.clinic.patient.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * STORY-001 characterization of {@link AuthService}: RULE-026, RULE-027, RULE-035 and RULE-045.
 * Passwords below are clearly-fake test values, not the legacy demo defaults.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("STORY-001 AuthService characterization")
class AuthServiceCharacterizationTest {

    private static final String FAKE_PASSWORD = "fake-pass-123"; // test-only value

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientService patientService;

    private BCryptPasswordEncoder passwordEncoder;
    private TokenService tokenService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = spy(new BCryptPasswordEncoder(4));
        tokenService = new TokenService();
        authService = new AuthService(userAccountRepository, patientRepository, patientService,
                passwordEncoder, tokenService);
        clearInvocations(passwordEncoder); // forget the constructor's dummy-hash encode
    }

    private static CustomerSignupRequest signup(LocalDate dob, String email) {
        CustomerSignupRequest r = new CustomerSignupRequest();
        r.setFirstName("Ana");
        r.setLastName("Lopez");
        r.setDateOfBirth(dob);
        r.setEmail(email);
        r.setTreatmentInterest(TreatmentInterest.IVF);
        r.setPassword(FAKE_PASSWORD);
        return r;
    }

    private static LoginRequest login(String username, String password) {
        LoginRequest r = new LoginRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    private Patient anaLopez() {
        Patient p = new Patient();
        p.setId(42L);
        p.setFirstName("Ana");
        p.setLastName("Lopez");
        return p;
    }

    private UserAccount customerAccount() {
        UserAccount a = new UserAccount();
        a.setId(5L);
        a.setUsername("laura.gomez@example.com");
        a.setPasswordHash(new BCryptPasswordEncoder(4).encode(FAKE_PASSWORD));
        a.setRole(Role.CUSTOMER);
        a.setDisplayName("Laura Gomez");
        return a;
    }

    // ---------------------------------------------------------------- RULE-026

    @Test
    @DisplayName("RULE-026 STORY-001 one day short of 18 -> 400 You must be at least 18 years old to register; nothing looked up")
    void RULE_026_STORY001_oneDayShortOfEighteenIsRefused() {
        LocalDate dob = LocalDate.now().minusYears(18).plusDays(1);

        assertThatThrownBy(() -> authService.registerCustomer(signup(dob, "ana.lopez@example.com")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("You must be at least 18 years old to register");
        verifyNoInteractions(userAccountRepository, patientRepository, patientService);
    }

    @Test
    @DisplayName("RULE-026 STORY-001 an 18th birthday today is old enough")
    void RULE_026_STORY001_eighteenthBirthdayTodayIsAccepted() {
        when(patientService.register(any())).thenReturn(anaLopez());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse response = authService.registerCustomer(
                signup(LocalDate.now().minusYears(18), "ana.lopez@example.com"));

        assertThat(response.getUser().getRole()).isEqualTo(Role.CUSTOMER);
    }

    // ---------------------------------------------------------------- RULE-027

    @Test
    @DisplayName("RULE-027 STORY-001 both checks use the trimmed, lowercased email")
    void RULE_027_STORY001_emailNormalisedBeforeBothChecks() {
        when(patientService.register(any())).thenReturn(anaLopez());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.registerCustomer(signup(LocalDate.of(1990, 1, 1), "  Ana.Lopez@Example.COM "));

        verify(userAccountRepository).existsByUsernameIgnoreCase("ana.lopez@example.com");
        verify(patientRepository).existsByEmailIgnoreCase("ana.lopez@example.com");
    }

    @Test
    @DisplayName("RULE-027 STORY-001 an existing login -> 409 An account with this email already exists. Please sign in.")
    void RULE_027_STORY001_existingAccountMessage() {
        when(userAccountRepository.existsByUsernameIgnoreCase("ana.lopez@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerCustomer(signup(LocalDate.of(1990, 1, 1), "Ana.Lopez@example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("An account with this email already exists. Please sign in.");
        verify(patientRepository, never()).existsByEmailIgnoreCase(anyString()); // account check comes first
        verify(patientService, never()).register(any());
    }

    @Test
    @DisplayName("RULE-027 STORY-001 an existing patient without login -> 409 This email is already registered with the clinic...")
    void RULE_027_STORY001_existingPatientMessage() {
        when(patientRepository.existsByEmailIgnoreCase("sofia.romano@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerCustomer(signup(LocalDate.of(1990, 1, 1), "sofia.romano@example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This email is already registered with the clinic. "
                        + "Please contact the clinic to set up portal access.");
        verify(patientService, never()).register(any());
        verify(userAccountRepository, never()).save(any(UserAccount.class));
    }

    // ---------------------------------------------------------------- RULE-035

    @Test
    @DisplayName("RULE-035 STORY-001 signup creates a CUSTOMER login linked to the new patient and returns a live token")
    void RULE_035_STORY001_signupCreatesLinkedCustomerAccountAndSignsIn() {
        Patient patient = anaLopez();
        CustomerSignupRequest request = signup(LocalDate.of(1990, 1, 1), " Ana.Lopez@Example.com ");
        when(patientService.register(request)).thenReturn(patient);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });

        LoginResponse response = authService.registerCustomer(request);

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        UserAccount account = captor.getValue();
        assertThat(account.getUsername()).isEqualTo("ana.lopez@example.com");
        assertThat(account.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(account.getDisplayName()).isEqualTo("Ana Lopez");
        assertThat(account.getPatient()).isSameAs(patient);
        assertThat(account.getCreatedAt()).isNotNull();
        assertThat(account.getPasswordHash()).startsWith("$2a$").isNotEqualTo(FAKE_PASSWORD);
        assertThat(new BCryptPasswordEncoder().matches(FAKE_PASSWORD, account.getPasswordHash())).isTrue();

        assertThat(response.getUser().getId()).isEqualTo(5L);
        assertThat(response.getUser().getPatientId()).isEqualTo(42L);
        assertThat(response.getUser().getDisplayName()).isEqualTo("Ana Lopez");
        assertThat(tokenService.resolve(response.getToken())).isPresent()
                .get().extracting(AuthenticatedUser::getPatientId).isEqualTo(42L);
    }

    // ---------------------------------------------------------------- RULE-045

    @Test
    @DisplayName("RULE-045 STORY-001 the username is trimmed (case is left to the IgnoreCase lookup) and a wrong password gives the uniform message")
    void RULE_045_STORY001_usernameTrimmedAndWrongPasswordUniformMessage() {
        when(userAccountRepository.findByUsernameIgnoreCase("Laura.Gomez@Example.com"))
                .thenReturn(Optional.of(customerAccount()));

        assertThatThrownBy(() -> authService.login(login(" Laura.Gomez@Example.com ", "wrong-pass")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email/username or password");
    }

    @Test
    @DisplayName("RULE-045 STORY-001 mixed-case username with the right password signs in")
    void RULE_045_STORY001_mixedCaseUsernameSignsIn() {
        when(userAccountRepository.findByUsernameIgnoreCase("Laura.Gomez@Example.com"))
                .thenReturn(Optional.of(customerAccount()));

        LoginResponse response = authService.login(login(" Laura.Gomez@Example.com ", FAKE_PASSWORD));

        assertThat(response.getUser().getUsername()).isEqualTo("laura.gomez@example.com");
    }

    @Test
    @DisplayName("RULE-045 STORY-001 an unknown user gets the same message and still costs exactly one BCrypt comparison")
    void RULE_045_STORY001_unknownUserSameMessageAndOneBcryptComparison() {
        when(userAccountRepository.findByUsernameIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(login("nobody@example.com", "whatever-pass")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email/username or password");
        verify(passwordEncoder, times(1)).matches(eq("whatever-pass"), anyString());
    }
}
