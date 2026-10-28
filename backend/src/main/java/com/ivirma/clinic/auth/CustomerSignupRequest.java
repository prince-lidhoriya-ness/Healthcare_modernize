package com.ivirma.clinic.auth;

import com.ivirma.clinic.patient.PatientRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Customer portal sign-up: the patient's details plus a password. The email becomes the username.
 */
public class CustomerSignupRequest extends PatientRequest {

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    public CustomerSignupRequest() {
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
