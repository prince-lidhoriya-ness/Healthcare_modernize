package com.ivirma.clinic.customer;

import com.ivirma.clinic.common.VisitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * A booking from the customer portal. The patient is always the signed-in customer.
 */
public class CustomerAppointmentRequest {

    @NotNull
    private VisitType visitType;

    @NotBlank
    private String clinician;

    @NotNull
    private LocalDateTime startTime;

    public CustomerAppointmentRequest() {
    }

    public VisitType getVisitType() { return visitType; }
    public void setVisitType(VisitType visitType) { this.visitType = visitType; }
    public String getClinician() { return clinician; }
    public void setClinician(String clinician) { this.clinician = clinician; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
}
