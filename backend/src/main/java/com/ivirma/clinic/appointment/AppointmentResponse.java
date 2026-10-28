package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.VisitType;

import java.time.LocalDateTime;

public class AppointmentResponse {

    private Long id;
    private Long patientId;
    private String patientName;
    private VisitType visitType;
    private String visitTypeLabel;
    private String clinician;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int durationMinutes;
    private AppointmentStatus status;

    public AppointmentResponse() {
    }

    public static AppointmentResponse from(Appointment a) {
        AppointmentResponse r = new AppointmentResponse();
        r.setId(a.getId());
        r.setPatientId(a.getPatient().getId());
        r.setPatientName(a.getPatient().getFirstName() + " " + a.getPatient().getLastName());
        r.setVisitType(a.getVisitType());
        r.setVisitTypeLabel(a.getVisitType().getLabel());
        r.setClinician(a.getClinician());
        r.setStartTime(a.getStartTime());
        r.setEndTime(a.getEndTime());
        r.setDurationMinutes(a.getDurationMinutes());
        r.setStatus(a.getStatus());
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public VisitType getVisitType() { return visitType; }
    public void setVisitType(VisitType visitType) { this.visitType = visitType; }
    public String getVisitTypeLabel() { return visitTypeLabel; }
    public void setVisitTypeLabel(String visitTypeLabel) { this.visitTypeLabel = visitTypeLabel; }
    public String getClinician() { return clinician; }
    public void setClinician(String clinician) { this.clinician = clinician; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
}
