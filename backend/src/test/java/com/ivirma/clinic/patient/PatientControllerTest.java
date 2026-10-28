package com.ivirma.clinic.patient;

import com.ivirma.clinic.common.NotFoundException;
import com.ivirma.clinic.common.TreatmentInterest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@AutoConfigureMockMvc(addFilters = false) // controller behaviour only; access rules are tested in SecurityRulesTest
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @Test
    void registerReturns201WithSavedPatient() throws Exception {
        Patient saved = new Patient();
        saved.setId(1L);
        saved.setFirstName("Test");
        saved.setLastName("Patient");
        saved.setDateOfBirth(LocalDate.of(1990, 5, 20));
        saved.setEmail("test.patient@example.com");
        saved.setTreatmentInterest(TreatmentInterest.EGG_FREEZING);
        saved.setCreatedAt(LocalDateTime.now());
        when(patientService.register(any(PatientRequest.class))).thenReturn(saved);

        String body = "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"dateOfBirth\":\"1990-05-20\","
                + "\"email\":\"test.patient@example.com\",\"treatmentInterest\":\"EGG_FREEZING\"}";

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.treatmentInterest").value("EGG_FREEZING"));
    }

    @Test
    void registerRejectsMissingFirstName() throws Exception {
        String body = "{\"lastName\":\"Patient\",\"dateOfBirth\":\"1990-05-20\","
                + "\"email\":\"test.patient@example.com\",\"treatmentInterest\":\"IVF\"}";

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerRejectsPatientUnder18() throws Exception {
        String dob = LocalDate.now().minusYears(17).toString();
        String body = "{\"firstName\":\"Test\",\"lastName\":\"Patient\",\"dateOfBirth\":\"" + dob + "\","
                + "\"email\":\"young@example.com\",\"treatmentInterest\":\"IVF\"}";

        mockMvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Patient must be at least 18 years old"));
    }

    @Test
    void getUnknownPatientReturns404() throws Exception {
        when(patientService.get(5L)).thenThrow(new NotFoundException("Patient 5 not found"));

        mockMvc.perform(get("/api/patients/5"))
                .andExpect(status().isNotFound());
    }
}
