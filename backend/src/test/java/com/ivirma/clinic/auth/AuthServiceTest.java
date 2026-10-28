package com.ivirma.clinic.auth;

import com.ivirma.clinic.common.BadRequestException;
import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.TreatmentInterest;
import com.ivirma.clinic.common.UnauthorizedException;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import com.ivirma.clinic.patient.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientService patientService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final TokenService tokenService = new TokenService();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userAccountRepository, patientRepository, patientService,
                passwordEncoder, tokenService);
    }

    private UserAccount admin() {
        UserAccount account = new UserAccount();
        account.setId(1L);
        account.setUsername("admin");
        account.setPasswordHash(passwordEncoder.encode("Admin@123"));
        account.setRole(Role.ADMIN);
        account.setDisplayName("Clinic coordinator");
        return account;
    }

    private LoginRequest login(String username, String password, Role portal) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setPortal(portal);
        return request;
    }

    private CustomerSignupRequest signup(LocalDate dateOfBirth) {
        CustomerSignupRequest request = new CustomerSignupRequest();
        request.setFirstName("Test");
        request.setLastName("Patient");
        request.setDateOfBirth(dateOfBirth);
        request.setEmail("Test.Patient@example.com");
        request.setTreatmentInterest(TreatmentInterest.IVF);
        request.setPassword("Secret123");
        return request;
    }

    @Test
    void loginReturnsTokenThatResolvesToUser() {
        when(userAccountRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin()));

        LoginResponse response = authService.login(login("admin", "Admin@123", Role.ADMIN));

        assertThat(response.getUser().getRole()).isEqualTo(Role.ADMIN);
        assertThat(tokenService.resolve(response.getToken())).isPresent();
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userAccountRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin()));

        assertThatThrownBy(() -> authService.login(login("admin", "wrong", Role.ADMIN)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void loginRejectsUnknownUserWithSameMessage() {
        when(userAccountRepository.findByUsernameIgnoreCase("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(login("nobody", "whatever", null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void loginRejectsAccountOfTheOtherPortal() {
        when(userAccountRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin()));

        assertThatThrownBy(() -> authService.login(login("admin", "Admin@123", Role.CUSTOMER)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("customer portal");
    }

    @Test
    void logoutRevokesToken() {
        when(userAccountRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin()));
        String token = authService.login(login("admin", "Admin@123", Role.ADMIN)).getToken();

        authService.logout(token);

        assertThat(tokenService.resolve(token)).isEmpty();
    }

    @Test
    void registerCustomerCreatesPatientAndLinkedAccount() {
        Patient patient = new Patient();
        patient.setId(42L);
        patient.setFirstName("Test");
        patient.setLastName("Patient");
        when(patientService.register(any())).thenReturn(patient);
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });

        LoginResponse response = authService.registerCustomer(signup(LocalDate.of(1990, 1, 1)));

        assertThat(response.getUser().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.getUser().getPatientId()).isEqualTo(42L);
        assertThat(response.getUser().getUsername()).isEqualTo("test.patient@example.com");
    }

    @Test
    void registerCustomerRejectsUnder18() {
        assertThatThrownBy(() -> authService.registerCustomer(signup(LocalDate.now().minusYears(17))))
                .isInstanceOf(BadRequestException.class);
        verify(patientService, never()).register(any());
    }

    @Test
    void registerCustomerRejectsExistingAccount() {
        when(userAccountRepository.existsByUsernameIgnoreCase("test.patient@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerCustomer(signup(LocalDate.of(1990, 1, 1))))
                .isInstanceOf(ConflictException.class);
        verify(patientService, never()).register(any());
    }
}
