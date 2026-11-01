package com.ivirma.clinic.patient;

import com.ivirma.clinic.common.TreatmentInterest;
import com.ivirma.clinic.testsupport.LogCapture;
import com.ivirma.clinic.testsupport.SyntheticPatient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ivirma.clinic.testsupport.SyntheticPatient.BIRTH_DATE;
import static com.ivirma.clinic.testsupport.SyntheticPatient.FIRST_NAME;
import static com.ivirma.clinic.testsupport.SyntheticPatient.ID;
import static com.ivirma.clinic.testsupport.SyntheticPatient.INTEREST;
import static com.ivirma.clinic.testsupport.SyntheticPatient.LAST_NAME;
import static com.ivirma.clinic.testsupport.SyntheticPatient.MAIL;
import static com.ivirma.clinic.testsupport.SyntheticPatient.TELEPHONE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * STORY-024 (SEC-007, CWE-532, CWE-117; TASK-071, TASK-072): registration, update and search write the
 * patient id and the action to the log, never the name, date of birth, email, phone, treatment interest or
 * the raw search term, and request input cannot break a log line. Synthetic data only.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("STORY-024 PatientService logs ids and actions only")
class PatientServiceLogPrivacyTest {

    private static final String NEW_FIRST_NAME = "Marisela";
    private static final String NEW_LAST_NAME = "Quintero";
    private static final LocalDate NEW_BIRTH_DATE = LocalDate.of(1986, 11, 30);
    private static final String NEW_MAIL = "m.quintero@example.net";
    private static final String NEW_TELEPHONE = "+34 600 000 418";
    private static final TreatmentInterest NEW_INTEREST = TreatmentInterest.EGG_DONATION;

    private static final String SEARCH_TERM = "Quintanil";

    @RegisterExtension
    final LogCapture captured = LogCapture.watching(PatientService.class);

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientSearchRepository patientSearchRepository;

    @InjectMocks
    private PatientService patientService;

    private static PatientRequest request(String first, String last, LocalDate born, String mail, String telephone,
                                          TreatmentInterest interest) {
        PatientRequest r = new PatientRequest();
        r.setFirstName(first);
        r.setLastName(last);
        r.setDateOfBirth(born);
        r.setEmail(mail);
        r.setPhone(telephone);
        r.setTreatmentInterest(interest);
        return r;
    }

    private static Map<String, Object> updatedPersonalData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("new first name", NEW_FIRST_NAME);
        data.put("new last name", NEW_LAST_NAME);
        data.put("new date of birth", NEW_BIRTH_DATE);
        data.put("new email address", NEW_MAIL);
        data.put("new phone number", NEW_TELEPHONE);
        data.put("new treatment interest", NEW_INTEREST);
        data.put("new treatment interest label", NEW_INTEREST.getLabel());
        return data;
    }

    private void saveAssignsId() {
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> {
            Patient p = inv.getArgument(0);
            p.setId(ID);
            return p;
        });
    }

    @Test
    @DisplayName("SEC-007 STORY-024 registration logs the new patient id, no personal data")
    void SEC_007_STORY024_registrationLogsThePatientIdOnly() {
        saveAssignsId();

        patientService.register(request(FIRST_NAME, LAST_NAME, BIRTH_DATE, MAIL, TELEPHONE, INTEREST));

        captured.assertInfoLineShows(ID);
        captured.assertNoLineCarries(SyntheticPatient.personalData());
    }

    @Test
    @DisplayName("SEC-007 STORY-024 update logs the patient id, neither the stored nor the new personal data")
    void SEC_007_STORY024_updateLogsThePatientIdOnly() {
        when(patientRepository.findById(ID)).thenReturn(Optional.of(SyntheticPatient.entity()));
        saveAssignsId();

        patientService.update(ID, request(NEW_FIRST_NAME, NEW_LAST_NAME, NEW_BIRTH_DATE, NEW_MAIL, NEW_TELEPHONE,
                NEW_INTEREST));

        captured.assertInfoLineShows(ID);
        captured.assertNoLineCarries(SyntheticPatient.personalData());
        captured.assertNoLineCarries(updatedPersonalData());
    }

    @Test
    @DisplayName("SEC-007 STORY-024 search logs the number of matches, neither the raw search term nor the matches")
    void SEC_007_STORY024_searchLogsTheMatchCountOnly() {
        Patient other = SyntheticPatient.entity();
        other.setId(ID + 1);
        other.setFirstName(NEW_FIRST_NAME);
        other.setLastName(NEW_LAST_NAME);
        when(patientSearchRepository.search(SEARCH_TERM)).thenReturn(List.of(SyntheticPatient.entity(), other));

        patientService.list(SEARCH_TERM);

        captured.assertInfoLineShows(2);
        Map<String, Object> data = SyntheticPatient.personalData();
        data.put("raw search term", SEARCH_TERM);
        data.put("matching first name", NEW_FIRST_NAME);
        data.put("matching last name", NEW_LAST_NAME);
        captured.assertNoLineCarries(data);
    }

    @Test
    @DisplayName("CWE-117 STORY-024 CR/LF in names or in the search term cannot start a forged log line")
    void CWE_117_STORY024_lineBreaksInRequestInputCannotForgeALogLine() {
        when(patientRepository.findById(ID)).thenReturn(Optional.of(SyntheticPatient.entity()));
        saveAssignsId();
        PatientRequest forged = request("Eve\r\n2026-11-02 10:00:00 INFO forged entry", "Example\nWARN forged entry",
                BIRTH_DATE, "eve.example@example.com", TELEPHONE, INTEREST);

        patientService.register(forged);
        patientService.update(ID, forged);
        patientService.list("Quint\r\nINFO forged search");

        captured.assertNoLineBreaks();
        captured.assertNoLineCarries(Map.of("forged request text", "forged"));
    }
}
