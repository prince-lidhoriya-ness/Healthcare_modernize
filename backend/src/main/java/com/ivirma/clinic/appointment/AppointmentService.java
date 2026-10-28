package com.ivirma.clinic.appointment;

import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.BadRequestException;
import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.NotFoundException;
import com.ivirma.clinic.doctor.DoctorService;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    static final LocalTime CLINIC_OPENS = LocalTime.of(8, 0);
    static final LocalTime CLINIC_CLOSES = LocalTime.of(19, 0);
    private static final int LONGEST_VISIT_MINUTES = 60;

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorService doctorService;

    public AppointmentService(AppointmentRepository appointmentRepository, PatientRepository patientRepository,
                              DoctorService doctorService) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorService = doctorService;
    }

    @Transactional
    public AppointmentResponse schedule(AppointmentRequest request) {
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new NotFoundException("Patient " + request.getPatientId() + " not found"));

        if (!doctorService.isKnown(request.getClinician())) {
            throw new BadRequestException("Unknown clinician: " + request.getClinician());
        }

        LocalDateTime start = request.getStartTime().withSecond(0).withNano(0);
        int duration = request.getVisitType().getDurationMinutes();
        LocalDateTime end = start.plusMinutes(duration);

        if (start.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Appointments cannot be booked in the past");
        }
        if (start.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new BadRequestException("The clinic is closed on Sundays");
        }
        if (start.toLocalTime().isBefore(CLINIC_OPENS) || end.toLocalTime().isAfter(CLINIC_CLOSES)
                || !end.toLocalDate().equals(start.toLocalDate())) {
            throw new BadRequestException("Appointments must be between 08:00 and 19:00");
        }

        // clinician conflict
        List<Appointment> clinicianAppointments = appointmentRepository.findByClinicianAndStatusNotAndStartTimeBetween(
                request.getClinician(), AppointmentStatus.CANCELLED, start.minusMinutes(LONGEST_VISIT_MINUTES), end);
        for (Appointment existing : clinicianAppointments) {
            if (existing.getStartTime().isBefore(end) && existing.getEndTime().isAfter(start)) {
                throw new ConflictException(request.getClinician() + " already has an appointment from "
                        + existing.getStartTime().toLocalTime() + " to " + existing.getEndTime().toLocalTime());
            }
        }

        // patient conflict
        List<Appointment> patientAppointments = appointmentRepository.findByPatientIdAndStatusNotAndStartTimeBetween(
                patient.getId(), AppointmentStatus.CANCELLED, start.minusMinutes(LONGEST_VISIT_MINUTES), end);
        for (Appointment existing : patientAppointments) {
            if (existing.getStartTime().isBefore(end) && existing.getEndTime().isAfter(start)) {
                throw new ConflictException("Patient " + patient.getFirstName() + " " + patient.getLastName()
                        + " already has an appointment from "
                        + existing.getStartTime().toLocalTime() + " to " + existing.getEndTime().toLocalTime());
            }
        }

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setVisitType(request.getVisitType());
        appointment.setClinician(request.getClinician());
        appointment.setStartTime(start);
        appointment.setDurationMinutes(duration);
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setCreatedAt(LocalDateTime.now());
        Appointment saved = appointmentRepository.save(appointment);

        log.info("Booked {} for patient {} {} (DOB {}, email {}) with {} at {}",
                request.getVisitType(), patient.getFirstName(), patient.getLastName(),
                patient.getDateOfBirth(), patient.getEmail(), request.getClinician(), start);
        return AppointmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(boolean upcomingOnly) {
        List<Appointment> appointments;
        if (upcomingOnly) {
            appointments = appointmentRepository.findByStatusAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
                    AppointmentStatus.SCHEDULED, LocalDateTime.now());
        } else {
            appointments = appointmentRepository.findAllByOrderByStartTimeDesc();
        }
        List<AppointmentResponse> result = new ArrayList<AppointmentResponse>();
        for (Appointment a : appointments) {
            result.add(AppointmentResponse.from(a));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listForPatient(Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new NotFoundException("Patient " + patientId + " not found");
        }
        List<AppointmentResponse> result = new ArrayList<AppointmentResponse>();
        for (Appointment a : appointmentRepository.findByPatientIdOrderByStartTimeDesc(patientId)) {
            result.add(AppointmentResponse.from(a));
        }
        return result;
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, AppointmentStatus newStatus) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Appointment " + id + " not found"));

        if (newStatus == AppointmentStatus.SCHEDULED) {
            throw new BadRequestException("Status can only be changed to COMPLETED, CANCELLED or NO_SHOW");
        }
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException("Appointment is already " + appointment.getStatus()
                    + " and can no longer be changed");
        }
        appointment.setStatus(newStatus);
        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment {} for {} {} changed to {}", id,
                appointment.getPatient().getFirstName(), appointment.getPatient().getLastName(), newStatus);
        return AppointmentResponse.from(saved);
    }

    /** Customer portal: a patient may cancel only their own appointments. */
    @Transactional
    public AppointmentResponse cancelForPatient(Long id, Long patientId) {
        Appointment appointment = appointmentRepository.findById(id)
                .filter(a -> a.getPatient().getId().equals(patientId))
                .orElseThrow(() -> new NotFoundException("Appointment " + id + " not found"));
        return updateStatus(appointment.getId(), AppointmentStatus.CANCELLED);
    }
}
