package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.AppointmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * STORY-001 characterization of the status endpoint's own check (RULE-033); the transitions
 * themselves are pinned in {@link AppointmentServiceCharacterizationTest}.
 */
@WebMvcTest(AppointmentController.class)
@AutoConfigureMockMvc(addFilters = false) // controller behaviour only; access rules are in SecurityCharacterizationTest
@DisplayName("STORY-001 AppointmentController characterization")
class AppointmentControllerCharacterizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentService appointmentService;

    @Test
    @DisplayName("RULE-033 STORY-001 PATCH status with no status -> 400 Status is required (service not called)")
    void RULE_033_STORY001_missingStatusIsRefusedByController() throws Exception {
        mockMvc.perform(patch("/api/appointments/12/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Status is required"));
        verify(appointmentService, never()).updateStatus(anyLong(), any(AppointmentStatus.class));
    }

    @Test
    @DisplayName("RULE-033 STORY-001 PATCH status SCHEDULED is passed to the service (which refuses it)")
    void RULE_033_STORY001_scheduledTargetReachesService() throws Exception {
        mockMvc.perform(patch("/api/appointments/12/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isOk());
        verify(appointmentService).updateStatus(12L, AppointmentStatus.SCHEDULED);
    }

    @Test
    @DisplayName("RULE-033 STORY-001 PATCH status with an unknown value -> 400 Request body is invalid")
    void RULE_033_STORY001_unknownStatusValueIsUnreadable() throws Exception {
        mockMvc.perform(patch("/api/appointments/12/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is invalid"));
    }
}
