package com.ivirma.clinic.patient;

import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.NotFoundException;
import com.ivirma.clinic.common.TreatmentInterest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * STORY-001 characterization of {@link PatientService}: RULE-024 (unique email at registration),
 * RULE-025 (unvalidated update, confirmed as intended) and DELTA-019 (commons-text capitalizeFully).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("STORY-001 PatientService characterization")
class PatientServiceCharacterizationTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientSearchRepository patientSearchRepository;

    @InjectMocks
    private PatientService patientService;

    private static PatientRequest request(String first, String last, String email) {
        PatientRequest r = new PatientRequest();
        r.setFirstName(first);
        r.setLastName(last);
        r.setDateOfBirth(LocalDate.of(1989, 4, 12));
        r.setEmail(email);
        r.setPhone("+34 600 000 001");
        r.setTreatmentInterest(TreatmentInterest.IVF);
        return r;
    }

    private Patient stored() {
        Patient p = new Patient();
        p.setId(3L);
        p.setFirstName("Laura");
        p.setLastName("Gomez");
        p.setDateOfBirth(LocalDate.of(1989, 4, 12));
        p.setEmail("laura.gomez@example.com");
        p.setPhone("+34 600 000 001");
        p.setTreatmentInterest(TreatmentInterest.IVF);
        p.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        return p;
    }

    private void saveReturnsArgument() {
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------------------------------------------------------------- RULE-024

    @Test
    @DisplayName("RULE-024 STORY-001 'Laura.Gomez@Example.com' already used (ignoring case) -> 409 with the email as typed")
    void RULE_024_STORY001_duplicateEmailIgnoringCaseIsRefused() {
        when(patientRepository.existsByEmailIgnoreCase("Laura.Gomez@Example.com")).thenReturn(true);

        assertThatThrownBy(() -> patientService.register(request("Laura", "Gomez", "Laura.Gomez@Example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A patient with email Laura.Gomez@Example.com already exists");
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    @DisplayName("RULE-024 STORY-001 quirk: the duplicate check uses the UNTRIMMED email, the trimmed lowercase one is saved")
    void RULE_024_STORY001_duplicateCheckUsesUntrimmedEmail() {
        saveReturnsArgument();

        Patient saved = patientService.register(request("Ana", "Lopez", " A@X.com"));

        verify(patientRepository).existsByEmailIgnoreCase(" A@X.com");
        assertThat(saved.getEmail()).isEqualTo("a@x.com");
    }

    // ---------------------------------------------------------------- RULE-025

    @Test
    @DisplayName("RULE-025 STORY-001 update overwrites every field exactly as sent: no trim, no title-case, no age check")
    void RULE_025_STORY001_updateStoresFieldsVerbatim() {
        when(patientRepository.findById(3L)).thenReturn(Optional.of(stored()));
        saveReturnsArgument();
        PatientRequest r = request("  laura  ", "de la cruz", "Laura.NEW@Example.com ");
        r.setDateOfBirth(LocalDate.of(2015, 1, 1)); // an 11-year-old: accepted on update
        r.setPhone(null);
        r.setTreatmentInterest(TreatmentInterest.IUI);

        Patient updated = patientService.update(3L, r);

        verify(patientRepository).existsByEmailIgnoreCaseAndIdNot("Laura.NEW@Example.com ", 3L);
        assertThat(updated.getFirstName()).isEqualTo("  laura  ");
        assertThat(updated.getLastName()).isEqualTo("de la cruz");
        assertThat(updated.getDateOfBirth()).isEqualTo(LocalDate.of(2015, 1, 1));
        assertThat(updated.getEmail()).isEqualTo("Laura.NEW@Example.com ");
        assertThat(updated.getPhone()).isNull();
        assertThat(updated.getTreatmentInterest()).isEqualTo(TreatmentInterest.IUI);
        assertThat(updated.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 9, 0)); // unchanged
    }

    @Test
    @DisplayName("RULE-025 STORY-001 update to an email held by another patient -> 409, nothing saved")
    void RULE_025_STORY001_updateRefusesEmailOfAnotherPatient() {
        when(patientRepository.findById(3L)).thenReturn(Optional.of(stored()));
        when(patientRepository.existsByEmailIgnoreCaseAndIdNot("sofia.romano@example.com", 3L)).thenReturn(true);

        assertThatThrownBy(() -> patientService.update(3L, request("Laura", "Gomez", "sofia.romano@example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A patient with email sofia.romano@example.com already exists");
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    @DisplayName("RULE-025 STORY-001 a null email skips the duplicate check and is written as null")
    void RULE_025_STORY001_nullEmailSkipsDuplicateCheck() {
        when(patientRepository.findById(3L)).thenReturn(Optional.of(stored()));
        saveReturnsArgument();

        Patient updated = patientService.update(3L, request("Laura", "Gomez", null));

        verify(patientRepository, never()).existsByEmailIgnoreCaseAndIdNot(anyString(), anyLong());
        assertThat(updated.getEmail()).isNull();
    }

    @Test
    @DisplayName("RULE-025 STORY-001 update of unknown patient 99 -> 404 Patient 99 not found")
    void RULE_025_STORY001_updateOfUnknownPatientIsNotFound() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.update(99L, request("Laura", "Gomez", "x@example.com")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Patient 99 not found");
    }

    // ---------------------------------------------------------------- DELTA-019 (RULE-010)

    @ParameterizedTest(name = "DELTA-019 STORY-001 capitalizeFully(\"{0}\") stores \"{1}\"")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "o'neil        | O'neil",
            "anne-marie    | Anne-marie",
            "DE LA CRUZ    | De La Cruz",
            "ÉLODIE        | Élodie",
            "\"  mARIA  \" | Maria",
            "McDonald      | Mcdonald"
    })
    void DELTA_019_STORY001_registerTitleCasesNamesWithCapitalizeFully(String typed, String expected) {
        saveReturnsArgument();

        Patient saved = patientService.register(request(typed, typed, "case-" + expected.length() + "@example.com"));

        ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(captor.capture());
        assertThat(captor.getValue().getFirstName()).isEqualTo(expected);
        assertThat(captor.getValue().getLastName()).isEqualTo(expected);
        assertThat(saved.getFullName()).isEqualTo(expected + " " + expected);
    }
}
