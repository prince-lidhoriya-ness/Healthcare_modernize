package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.patient.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointment", indexes = {
        @Index(name = "idx_appointment_clinician_start", columnList = "clinician, start_time"),
        @Index(name = "idx_appointment_patient", columnList = "patient_id")
})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private VisitType visitType;

    @Column(nullable = false, length = 100)
    private String clinician;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Appointment() {
    }

    public LocalDateTime getEndTime() {
        return startTime.plusMinutes(durationMinutes);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Patient getPatient() { return patient; }
    public void setPatient(Patient patient) { this.patient = patient; }
    public VisitType getVisitType() { return visitType; }
    public void setVisitType(VisitType visitType) { this.visitType = visitType; }
    public String getClinician() { return clinician; }
    public void setClinician(String clinician) { this.clinician = clinician; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
