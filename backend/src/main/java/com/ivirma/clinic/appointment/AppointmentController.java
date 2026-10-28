package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> schedule(@RequestBody AppointmentRequest request) {
        if (request.getPatientId() == null) {
            throw new BadRequestException("Patient is required");
        }
        if (request.getVisitType() == null) {
            throw new BadRequestException("Visit type is required");
        }
        if (isBlank(request.getClinician())) {
            throw new BadRequestException("Clinician is required");
        }
        if (request.getStartTime() == null) {
            throw new BadRequestException("Start time is required");
        }
        AppointmentResponse saved = appointmentService.schedule(request);
        return new ResponseEntity<AppointmentResponse>(saved, HttpStatus.CREATED);
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> list(
            @RequestParam(name = "upcoming", required = false, defaultValue = "true") boolean upcoming) {
        return appointmentService.list(upcoming);
    }

    @GetMapping("/patients/{patientId}/appointments")
    public List<AppointmentResponse> listForPatient(@PathVariable("patientId") Long patientId) {
        return appointmentService.listForPatient(patientId);
    }

    @PatchMapping("/appointments/{id}/status")
    public AppointmentResponse updateStatus(@PathVariable("id") Long id, @RequestBody StatusUpdateRequest request) {
        if (request.getStatus() == null) {
            throw new BadRequestException("Status is required");
        }
        return appointmentService.updateStatus(id, request.getStatus());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
