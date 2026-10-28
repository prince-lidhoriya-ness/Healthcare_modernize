package com.ivirma.clinic.dashboard;

import com.ivirma.clinic.appointment.AppointmentResponse;

import java.util.List;
import java.util.Map;

public class DashboardSummary {

    private long totalPatients;
    private long newPatientsThisWeek;
    private long appointmentsToday;
    private long upcomingNext7Days;
    private Map<String, Long> appointmentsByStatus;
    private Map<String, Long> appointmentsByVisitType;
    private List<AppointmentResponse> nextAppointments;
    private String generatedAt;

    public DashboardSummary() {
    }

    public long getTotalPatients() { return totalPatients; }
    public void setTotalPatients(long totalPatients) { this.totalPatients = totalPatients; }
    public long getNewPatientsThisWeek() { return newPatientsThisWeek; }
    public void setNewPatientsThisWeek(long newPatientsThisWeek) { this.newPatientsThisWeek = newPatientsThisWeek; }
    public long getAppointmentsToday() { return appointmentsToday; }
    public void setAppointmentsToday(long appointmentsToday) { this.appointmentsToday = appointmentsToday; }
    public long getUpcomingNext7Days() { return upcomingNext7Days; }
    public void setUpcomingNext7Days(long upcomingNext7Days) { this.upcomingNext7Days = upcomingNext7Days; }
    public Map<String, Long> getAppointmentsByStatus() { return appointmentsByStatus; }
    public void setAppointmentsByStatus(Map<String, Long> appointmentsByStatus) { this.appointmentsByStatus = appointmentsByStatus; }
    public Map<String, Long> getAppointmentsByVisitType() { return appointmentsByVisitType; }
    public void setAppointmentsByVisitType(Map<String, Long> appointmentsByVisitType) { this.appointmentsByVisitType = appointmentsByVisitType; }
    public List<AppointmentResponse> getNextAppointments() { return nextAppointments; }
    public void setNextAppointments(List<AppointmentResponse> nextAppointments) { this.nextAppointments = nextAppointments; }
    public String getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(String generatedAt) { this.generatedAt = generatedAt; }
}
