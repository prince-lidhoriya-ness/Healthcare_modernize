package com.ivirma.clinic.config;

import com.ivirma.clinic.auth.Role;
import com.ivirma.clinic.auth.UserAccount;
import com.ivirma.clinic.auth.UserAccountRepository;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import com.ivirma.clinic.testsupport.TestSecrets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * STORY-001 characterization of {@link DemoAccountSeeder} (RULE-039).
 *
 * <p>SECRET HANDLING: the legacy defaults for the demo passwords (application.properties:20-21)
 * are NOT copied here. The seeder takes them as constructor arguments, so these tests pass
 * random per-run values from {@link TestSecrets} instead (no password literal in the test).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("STORY-001 DemoAccountSeeder characterization")
class DemoAccountSeederCharacterizationTest {

    private static final String FAKE_ADMIN_PASSWORD = TestSecrets.password("ADMIN_PASSWORD");
    private static final String FAKE_CUSTOMER_PASSWORD = TestSecrets.password("CUSTOMER_PASSWORD");

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private PatientRepository patientRepository;

    private BCryptPasswordEncoder encoder;
    private DemoAccountSeeder seeder;

    @BeforeEach
    void setUp() {
        encoder = spy(new BCryptPasswordEncoder(4));
        seeder = new DemoAccountSeeder(userAccountRepository, patientRepository, encoder,
                "Admin", FAKE_ADMIN_PASSWORD, FAKE_CUSTOMER_PASSWORD);
    }

    private static Patient patient(long id, String first, String last, String email) {
        Patient p = new Patient();
        p.setId(id);
        p.setFirstName(first);
        p.setLastName(last);
        p.setEmail(email);
        return p;
    }

    @Test
    @DisplayName("RULE-039 STORY-001 0 accounts + 2 patients -> 3 accounts: admin first, one CUSTOMER per patient sharing ONE hash")
    @SuppressWarnings("unchecked")
    void RULE_039_STORY001_seedsAdminPlusEveryPatientOnSharedPassword() {
        Patient laura = patient(1L, "Laura", "Gomez", "Laura.Gomez@Example.com");
        Patient sofia = patient(2L, "Sofia", "Romano", "sofia.romano@example.com");
        when(userAccountRepository.count()).thenReturn(0L);
        when(patientRepository.findAll()).thenReturn(List.of(laura, sofia));

        seeder.run();

        ArgumentCaptor<Iterable<UserAccount>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(userAccountRepository).saveAll(captor.capture());
        List<UserAccount> saved = new ArrayList<UserAccount>();
        captor.getValue().forEach(saved::add);
        assertThat(saved).hasSize(3);

        UserAccount admin = saved.get(0);
        assertThat(admin.getUsername()).isEqualTo("admin"); // lowercased
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getDisplayName()).isEqualTo("Clinic coordinator");
        assertThat(admin.getPatient()).isNull();
        assertThat(admin.getCreatedAt()).isNotNull();
        assertThat(encoder.matches(FAKE_ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();

        UserAccount first = saved.get(1);
        UserAccount second = saved.get(2);
        assertThat(first.getUsername()).isEqualTo("laura.gomez@example.com");
        assertThat(first.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(first.getDisplayName()).isEqualTo("Laura Gomez");
        assertThat(first.getPatient()).isSameAs(laura);
        assertThat(second.getUsername()).isEqualTo("sofia.romano@example.com");
        assertThat(second.getDisplayName()).isEqualTo("Sofia Romano");
        assertThat(second.getPatient()).isSameAs(sofia);
        assertThat(second.getPasswordHash()).isEqualTo(first.getPasswordHash()); // the same hash string
        assertThat(encoder.matches(FAKE_CUSTOMER_PASSWORD, first.getPasswordHash())).isTrue();

        verify(encoder, times(2)).encode(anyString()); // admin + one shared customer hash
    }

    @Test
    @DisplayName("RULE-039 STORY-001 if any account exists, nothing is seeded and patients are not read")
    void RULE_039_STORY001_skippedWhenAnyAccountExists() {
        when(userAccountRepository.count()).thenReturn(1L);

        seeder.run();

        verify(userAccountRepository, never()).saveAll(anyIterable());
        verifyNoInteractions(patientRepository);
        verify(encoder, never()).encode(anyString());
    }
}
