package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.VisitType;

import java.time.LocalDateTime;

public class AppointmentRequest {

    private Long patientId;
    private VisitType visitType;
    private String clinician;
    private LocalDateTime startTime;

    public AppointmentRequest() {
    }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public VisitType getVisitType() { return visitType; }
    public void setVisitType(VisitType visitType) { this.visitType = visitType; }
    public String getClinician() { return clinician; }
    public void setClinician(String clinician) { this.clinician = clinician; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
}
