package com.ivirma.clinic.customer;

import com.ivirma.clinic.appointment.AppointmentRequest;
import com.ivirma.clinic.appointment.AppointmentResponse;
import com.ivirma.clinic.appointment.AppointmentService;
import com.ivirma.clinic.auth.AuthenticatedUser;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer portal API. Every call works on the signed-in customer's own patient record;
 * the patient id never comes from the request.
 */
@RestController
@RequestMapping("/api/customer")
public class CustomerPortalController {

    private final PatientService patientService;
    private final AppointmentService appointmentService;

    public CustomerPortalController(PatientService patientService, AppointmentService appointmentService) {
        this.patientService = patientService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/profile")
    public Patient profile(@AuthenticationPrincipal AuthenticatedUser user) {
        return patientService.get(user.getPatientId());
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments(@AuthenticationPrincipal AuthenticatedUser user) {
        return appointmentService.listForPatient(user.getPatientId());
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> book(@AuthenticationPrincipal AuthenticatedUser user,
                                                    @Valid @RequestBody CustomerAppointmentRequest request) {
        AppointmentRequest booking = new AppointmentRequest();
        booking.setPatientId(user.getPatientId());
        booking.setVisitType(request.getVisitType());
        booking.setClinician(request.getClinician());
        booking.setStartTime(request.getStartTime());
        return new ResponseEntity<AppointmentResponse>(appointmentService.schedule(booking), HttpStatus.CREATED);
    }

    @PatchMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable("id") Long id) {
        return appointmentService.cancelForPatient(id, user.getPatientId());
    }
}
