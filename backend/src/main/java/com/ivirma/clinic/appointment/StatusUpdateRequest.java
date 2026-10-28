package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.AppointmentStatus;

public class StatusUpdateRequest {

    private AppointmentStatus status;

    public StatusUpdateRequest() {
    }

    public AppointmentStatus getStatus() { return status; }
    public void setStatus(AppointmentStatus status) { this.status = status; }
}
