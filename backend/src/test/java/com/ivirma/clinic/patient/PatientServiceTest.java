package com.ivirma.clinic.patient;

import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.NotFoundException;
import com.ivirma.clinic.common.TreatmentInterest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientSearchRepository patientSearchRepository;

    @InjectMocks
    private PatientService patientService;

    private PatientRequest validRequest() {
        PatientRequest r = new PatientRequest();
        r.setFirstName("test");
        r.setLastName("patient");
        r.setDateOfBirth(LocalDate.of(1990, 5, 20));
        r.setEmail("Test.Patient@Example.com");
        r.setPhone("+34 600 111 222");
        r.setTreatmentInterest(TreatmentInterest.IVF);
        return r;
    }

    @Test
    void registerSavesPatientWithNormalisedNameAndEmail() {
        when(patientRepository.existsByEmailIgnoreCase("Test.Patient@Example.com")).thenReturn(false);
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });

        Patient saved = patientService.register(validRequest());

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        Patient stored = captor.getValue();
        assertThat(saved.getId()).isEqualTo(42L);
        assertThat(stored.getFirstName()).isEqualTo("Test");
        assertThat(stored.getLastName()).isEqualTo("Patient");
        assertThat(stored.getEmail()).isEqualTo("test.patient@example.com");
        assertThat(stored.getTreatmentInterest()).isEqualTo(TreatmentInterest.IVF);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(patientRepository.existsByEmailIgnoreCase("Test.Patient@Example.com")).thenReturn(true);

        assertThatThrownBy(() -> patientService.register(validRequest()))
                .isInstanceOf(ConflictException.class);
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void getThrowsWhenPatientMissing() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.get(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void listWithoutSearchReturnsAllPatients() {
        patientService.list(null);
        verify(patientRepository).findAllByOrderByLastNameAscFirstNameAsc();
    }
}
