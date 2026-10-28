package com.ivirma.clinic.patient;

import com.ivirma.clinic.common.TreatmentInterest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class PatientRequest {

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotNull
    @Past
    private LocalDate dateOfBirth;

    @NotBlank
    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 30)
    private String phone;

    @NotNull
    private TreatmentInterest treatmentInterest;

    public PatientRequest() {
    }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public TreatmentInterest getTreatmentInterest() { return treatmentInterest; }
    public void setTreatmentInterest(TreatmentInterest treatmentInterest) { this.treatmentInterest = treatmentInterest; }
}
