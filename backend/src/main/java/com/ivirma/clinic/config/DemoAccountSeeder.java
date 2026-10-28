package com.ivirma.clinic.config;

import com.ivirma.clinic.auth.Role;
import com.ivirma.clinic.auth.UserAccount;
import com.ivirma.clinic.auth.UserAccountRepository;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates the demo logins the first time the app starts with no accounts:
 * one admin (clinic staff) account, and a customer account for every patient already in the database
 * (username = the patient's email). Runs after DemoDataSeeder.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "clinic.seed-demo-data", havingValue = "true")
public class DemoAccountSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final UserAccountRepository userAccountRepository;
    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String customerPassword;

    public DemoAccountSeeder(UserAccountRepository userAccountRepository, PatientRepository patientRepository,
                             PasswordEncoder passwordEncoder,
                             @Value("${clinic.demo-accounts.admin-username}") String adminUsername,
                             @Value("${clinic.demo-accounts.admin-password}") String adminPassword,
                             @Value("${clinic.demo-accounts.customer-password}") String customerPassword) {
        this.userAccountRepository = userAccountRepository;
        this.patientRepository = patientRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.customerPassword = customerPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userAccountRepository.count() > 0) {
            log.info("User accounts already exist - skipping demo account seed");
            return;
        }

        List<UserAccount> accounts = new ArrayList<UserAccount>();
        accounts.add(account(adminUsername, adminPassword, Role.ADMIN, "Clinic coordinator", null));

        String customerHash = passwordEncoder.encode(customerPassword);
        for (Patient patient : patientRepository.findAll()) {
            UserAccount customer = account(patient.getEmail(), null, Role.CUSTOMER, patient.getFullName(), patient);
            customer.setPasswordHash(customerHash);
            accounts.add(customer);
        }
        userAccountRepository.saveAll(accounts);

        log.info("Seeded 1 admin account and {} customer accounts", accounts.size() - 1);
    }

    private UserAccount account(String username, String password, Role role, String displayName, Patient patient) {
        UserAccount account = new UserAccount();
        account.setUsername(username.toLowerCase());
        if (password != null) {
            account.setPasswordHash(passwordEncoder.encode(password));
        }
        account.setRole(role);
        account.setDisplayName(displayName);
        account.setPatient(patient);
        account.setCreatedAt(LocalDateTime.now());
        return account;
    }
}
