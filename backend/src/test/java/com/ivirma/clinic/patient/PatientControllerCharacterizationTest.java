package com.ivirma.clinic.patient;

import com.ivirma.clinic.common.TreatmentInterest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-001 characterization of {@link PatientController}: RULE-022 (staff registration checks,
 * age 18+) and RULE-025 (PUT has no {@code @Valid}). Dates of birth are relative to the real
 * {@code LocalDate.now()}, which the controller uses.
 *
 * <p>Bean Validation messages are the English defaults (the build container runs with an English
 * locale); see the test README.
 */
@WebMvcTest(PatientController.class)
@AutoConfigureMockMvc(addFilters = false) // controller behaviour only; access rules are in SecurityCharacterizationTest
@DisplayName("STORY-001 PatientController characterization")
class PatientControllerCharacterizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    private static String body(String dob, String email, String interest) {
        return "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"dateOfBirth\":\"" + dob + "\","
                + "\"email\":\"" + email + "\"" + (interest == null ? "" : ",\"treatmentInterest\":\"" + interest + "\"")
                + "}";
    }

    private void registerReturnsAPatient() {
        Patient saved = new Patient();
        saved.setId(1L);
        when(patientService.register(any(PatientRequest.class))).thenReturn(saved);
    }

    // ---------------------------------------------------------------- RULE-022

    @Test
    @DisplayName("RULE-022 STORY-001 a patient whose 18th birthday is today is accepted (201)")
    void RULE_022_STORY001_eighteenthBirthdayTodayIsAccepted() throws Exception {
        registerReturnsAPatient();
        String dob = LocalDate.now().minusYears(18).toString();

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(body(dob, "adult@example.com", "IVF")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RULE-022 STORY-001 one day short of 18 -> 400 Patient must be at least 18 years old")
    void RULE_022_STORY001_oneDayShortOfEighteenIsRefused() throws Exception {
        String dob = LocalDate.now().minusYears(18).plusDays(1).toString();

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(body(dob, "young@example.com", "IVF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Patient must be at least 18 years old"));
        verify(patientService, never()).register(any(PatientRequest.class));
    }

    // NOTE: BUSINESS_RULES.md RULE-023 quotes "must be a date in the past"; the legacy (Hibernate Validator 8)
    // actually answers "must be a past date". The legacy is the oracle, so that is what is pinned here.
    @Test
    @DisplayName("RULE-022 STORY-001 a future DOB is caught by @Past first: 400 dateOfBirth: must be a past date")
    void RULE_022_STORY001_futureDobRefusedByBeanValidationMessage() throws Exception {
        String dob = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(body(dob, "future@example.com", "IVF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("dateOfBirth: must be a past date"));
    }

    @Test
    @DisplayName("RULE-022 STORY-001 DOB equal to today -> 400 dateOfBirth: must be a past date")
    void RULE_022_STORY001_dobTodayIsRefused() throws Exception {
        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(body(LocalDate.now().toString(), "today@example.com", "IVF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("dateOfBirth: must be a past date"));
    }

    @Test
    @DisplayName("RULE-022 STORY-001 email without '@' -> 400 email: must be a well-formed email address")
    void RULE_022_STORY001_emailWithoutAtIsRefused() throws Exception {
        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(body("1990-05-20", "laura.example.com", "IVF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));
    }

    @Test
    @DisplayName("RULE-022 STORY-001 missing treatment interest -> 400 treatmentInterest: must not be null")
    void RULE_022_STORY001_treatmentInterestIsRequired() throws Exception {
        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(body("1990-05-20", "nointerest@example.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("treatmentInterest: must not be null"));
    }

    // ---------------------------------------------------------------- RULE-024 over HTTP

    /**
     * BUSINESS_RULES.md RULE-024 notes that a space-padded email (" a@example.com") passes the (untrimmed)
     * duplicate check and then hits the DB unique constraint with a 500. Over HTTP that path is unreachable
     * today: {@code @Email} rejects the padding first. The service-level quirk is pinned in
     * PatientServiceCharacterizationTest.
     */
    @Test
    @DisplayName("RULE-024 STORY-001 POST /api/patients with a space-padded email -> 400 from @Email (service never reached)")
    void RULE_024_STORY001_paddedEmailIsRejectedByBeanValidationOverHttp() throws Exception {
        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content(body("1990-05-20", " laura.gomez@example.com", "IVF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("email: must be a well-formed email address"));
        verify(patientService, never()).register(any(PatientRequest.class));
    }

    // ---------------------------------------------------------------- RULE-025

    @Test
    @DisplayName("RULE-025 STORY-001 PUT with a blank name, an 11-year-old DOB and a malformed email -> 200 (no validation)")
    void RULE_025_STORY001_putIsNotValidated() throws Exception {
        Patient updated = new Patient();
        updated.setId(3L);
        when(patientService.update(eq(3L), any(PatientRequest.class))).thenReturn(updated);
        String body = "{\"firstName\":\"\",\"lastName\":\"gomez\",\"dateOfBirth\":\"2015-01-01\","
                + "\"email\":\"not-an-email\",\"treatmentInterest\":\"IVF\"}";

        mockMvc.perform(put("/api/patients/3").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3));

        ArgumentCaptor<PatientRequest> captor = ArgumentCaptor.forClass(PatientRequest.class);
        verify(patientService).update(eq(3L), captor.capture());
        assertThat(captor.getValue().getFirstName()).isEmpty();
        assertThat(captor.getValue().getLastName()).isEqualTo("gomez");
        assertThat(captor.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(2015, 1, 1));
        assertThat(captor.getValue().getEmail()).isEqualTo("not-an-email");
        assertThat(captor.getValue().getTreatmentInterest()).isEqualTo(TreatmentInterest.IVF);
    }
}
