package com.ivirma.clinic.auth;

import com.ivirma.clinic.appointment.AppointmentService;
import com.ivirma.clinic.customer.CustomerPortalController;
import com.ivirma.clinic.doctor.Doctor;
import com.ivirma.clinic.doctor.DoctorController;
import com.ivirma.clinic.doctor.DoctorService;
import com.ivirma.clinic.patient.PatientController;
import com.ivirma.clinic.patient.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may call what: admin API for staff only, customer API for customers only.
 */
@WebMvcTest(controllers = {PatientController.class, DoctorController.class, CustomerPortalController.class,
        AuthController.class})
@Import({SecurityConfig.class, TokenService.class})
class SecurityRulesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @MockBean
    private PatientService patientService;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private AuthService authService;

    @MockBean
    private DoctorService doctorService;

    private String adminToken() {
        return "Bearer " + tokenService.issue(new AuthenticatedUser(1L, "admin", Role.ADMIN, "Admin", null));
    }

    private String customerToken() {
        return "Bearer " + tokenService.issue(
                new AuthenticatedUser(2L, "test@example.com", Role.CUSTOMER, "Test Patient", 42L));
    }

    @Test
    void adminApiRequiresSignIn() throws Exception {
        mockMvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please sign in"));
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/patients").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanUseAdminApi() throws Exception {
        when(patientService.list(null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/patients").header("Authorization", adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotUseAdminApi() throws Exception {
        mockMvc.perform(get("/api/patients").header("Authorization", customerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void onlyAdminCanRegisterDoctors() throws Exception {
        String body = "{\"firstName\":\"Ana\",\"lastName\":\"Lopez\",\"specialty\":\"EMBRYOLOGY\","
                + "\"email\":\"ana.lopez@clinic.example.com\"}";

        mockMvc.perform(post("/api/doctors").header("Authorization", customerToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        when(doctorService.register(any())).thenReturn(new Doctor());
        mockMvc.perform(post("/api/doctors").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void registerDoctorValidatesInput() throws Exception {
        mockMvc.perform(post("/api/doctors").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Ana\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerSeesOnlyOwnAppointments() throws Exception {
        when(appointmentService.listForPatient(42L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/customer/appointments").header("Authorization", customerToken()))
                .andExpect(status().isOk());
        verify(appointmentService).listForPatient(42L);
    }

    @Test
    void adminCannotUseCustomerApi() throws Exception {
        mockMvc.perform(get("/api/customer/appointments").header("Authorization", adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginIsOpenToEveryone() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(1L, "admin", Role.ADMIN, "Admin", null);
        when(authService.login(any(LoginRequest.class))).thenReturn(new LoginResponse("abc", user));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("abc"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void meReturnsSignedInUser() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.patientId").value(42));
    }
}
