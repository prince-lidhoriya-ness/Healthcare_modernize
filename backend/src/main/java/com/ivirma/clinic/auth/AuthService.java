package com.ivirma.clinic.auth;

import com.ivirma.clinic.common.BadRequestException;
import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.UnauthorizedException;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import com.ivirma.clinic.patient.PatientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String INVALID_LOGIN = "Invalid email/username or password";

    private final UserAccountRepository userAccountRepository;
    private final PatientRepository patientRepository;
    private final PatientService patientService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    // Compared against when the username is unknown, so a wrong username takes as long as a wrong password
    private final String dummyHash;

    public AuthService(UserAccountRepository userAccountRepository, PatientRepository patientRepository,
                       PatientService patientService, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userAccountRepository = userAccountRepository;
        this.patientRepository = patientRepository;
        this.patientService = patientService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<UserAccount> found = userAccountRepository.findByUsernameIgnoreCase(request.getUsername().trim());
        if (found.isEmpty()) {
            passwordEncoder.matches(request.getPassword(), dummyHash);
            throw new UnauthorizedException(INVALID_LOGIN);
        }
        UserAccount account = found.get();
        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            log.info("Failed sign-in for account {}", account.getId());
            throw new UnauthorizedException(INVALID_LOGIN);
        }
        if (request.getPortal() != null && request.getPortal() != account.getRole()) {
            throw new UnauthorizedException(request.getPortal() == Role.ADMIN
                    ? "This account cannot sign in to the admin portal. Use the customer portal."
                    : "This account cannot sign in to the customer portal. Use the admin portal.");
        }
        log.info("Account {} signed in ({})", account.getId(), account.getRole());
        AuthenticatedUser user = AuthenticatedUser.from(account);
        return new LoginResponse(tokenService.issue(user), user);
    }

    @Transactional
    public LoginResponse registerCustomer(CustomerSignupRequest request) {
        if (Period.between(request.getDateOfBirth(), LocalDate.now()).getYears() < 18) {
            throw new BadRequestException("You must be at least 18 years old to register");
        }
        String email = request.getEmail().trim().toLowerCase();
        if (userAccountRepository.existsByUsernameIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists. Please sign in.");
        }
        if (patientRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("This email is already registered with the clinic. "
                    + "Please contact the clinic to set up portal access.");
        }

        Patient patient = patientService.register(request);

        UserAccount account = new UserAccount();
        account.setUsername(email);
        account.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        account.setRole(Role.CUSTOMER);
        account.setDisplayName(patient.getFullName());
        account.setPatient(patient);
        account.setCreatedAt(LocalDateTime.now());
        account = userAccountRepository.save(account);
        log.info("Customer account {} created for patient {}", account.getId(), patient.getId());

        AuthenticatedUser user = AuthenticatedUser.from(account);
        return new LoginResponse(tokenService.issue(user), user);
    }

    public void logout(String token) {
        tokenService.revoke(token);
    }
}
