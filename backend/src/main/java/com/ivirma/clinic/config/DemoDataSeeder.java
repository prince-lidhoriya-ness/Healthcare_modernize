package com.ivirma.clinic.config;

import com.ivirma.clinic.appointment.Appointment;
import com.ivirma.clinic.appointment.AppointmentRepository;
import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.TreatmentInterest;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.patient.Patient;
import com.ivirma.clinic.patient.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads SYNTHETIC demo data (fictional people, example.com emails) the first
 * time the application starts against an empty database.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "clinic.seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public DemoDataSeeder(PatientRepository patientRepository, AppointmentRepository appointmentRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (patientRepository.count() > 0) {
            log.info("Database already has data - skipping demo seed");
            return;
        }

        List<Patient> p = new ArrayList<Patient>();
        p.add(patient("Laura", "Gomez", "1989-04-12", TreatmentInterest.IVF, 0));
        p.add(patient("Sofia", "Romano", "1992-09-03", TreatmentInterest.EGG_FREEZING, 1));
        p.add(patient("Hannah", "Clarke", "1986-01-27", TreatmentInterest.EGG_DONATION, 2));
        p.add(patient("Aisha", "Rahman", "1990-06-18", TreatmentInterest.FERTILITY_ASSESSMENT, 3));
        p.add(patient("Marta", "Silva", "1988-11-05", TreatmentInterest.IUI, 4));
        p.add(patient("Chloe", "Martin", "1994-02-14", TreatmentInterest.EGG_FREEZING, 5));
        p.add(patient("Daniel", "Weber", "1985-07-30", TreatmentInterest.FERTILITY_ASSESSMENT, 6));
        p.add(patient("Nora", "Jensen", "1991-03-09", TreatmentInterest.IVF, 7));
        p = patientRepository.saveAll(p);

        String ruiz = "Dr. Elena Ruiz";
        String bianchi = "Dr. Marco Bianchi";
        String collins = "Dr. Sarah Collins";
        String nair = "Dr. Priya Nair";

        List<Appointment> a = new ArrayList<Appointment>();
        // past visits
        a.add(appt(p.get(0), VisitType.INITIAL_CONSULTATION, ruiz, -9, 9, 0, AppointmentStatus.COMPLETED));
        a.add(appt(p.get(1), VisitType.INITIAL_CONSULTATION, bianchi, -8, 10, 0, AppointmentStatus.COMPLETED));
        a.add(appt(p.get(2), VisitType.INITIAL_CONSULTATION, collins, -7, 11, 0, AppointmentStatus.COMPLETED));
        a.add(appt(p.get(0), VisitType.BLOOD_TEST, ruiz, -6, 9, 30, AppointmentStatus.COMPLETED));
        a.add(appt(p.get(3), VisitType.INITIAL_CONSULTATION, nair, -5, 12, 0, AppointmentStatus.NO_SHOW));
        a.add(appt(p.get(0), VisitType.ULTRASOUND_MONITORING, ruiz, -4, 9, 0, AppointmentStatus.COMPLETED));
        a.add(appt(p.get(4), VisitType.INITIAL_CONSULTATION, bianchi, -3, 15, 0, AppointmentStatus.CANCELLED));
        a.add(appt(p.get(6), VisitType.INITIAL_CONSULTATION, nair, -2, 10, 0, AppointmentStatus.COMPLETED));
        // upcoming visits
        a.add(appt(p.get(0), VisitType.EGG_RETRIEVAL, ruiz, 1, 9, 0, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(1), VisitType.ULTRASOUND_MONITORING, bianchi, 1, 10, 30, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(5), VisitType.INITIAL_CONSULTATION, collins, 1, 11, 0, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(7), VisitType.INITIAL_CONSULTATION, nair, 2, 9, 0, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(4), VisitType.FOLLOW_UP, bianchi, 2, 14, 0, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(2), VisitType.BLOOD_TEST, collins, 3, 10, 0, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(3), VisitType.INITIAL_CONSULTATION, nair, 4, 12, 0, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(0), VisitType.EMBRYO_TRANSFER, ruiz, 5, 9, 30, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(6), VisitType.FOLLOW_UP, nair, 6, 16, 0, AppointmentStatus.SCHEDULED));
        a.add(appt(p.get(1), VisitType.FOLLOW_UP, bianchi, 12, 10, 0, AppointmentStatus.SCHEDULED));
        appointmentRepository.saveAll(a);

        log.info("Seeded {} synthetic patients and {} appointments", p.size(), a.size());
    }

    private Patient patient(String first, String last, String dob, TreatmentInterest interest, int index) {
        Patient patient = new Patient();
        patient.setFirstName(first);
        patient.setLastName(last);
        patient.setDateOfBirth(LocalDate.parse(dob));
        patient.setEmail(first.toLowerCase() + "." + last.toLowerCase() + "@example.com");
        patient.setPhone("+34 600 000 00" + (index + 1));
        patient.setTreatmentInterest(interest);
        patient.setCreatedAt(LocalDateTime.now().minusDays(30 - index * 4L));
        return patient;
    }

    private Appointment appt(Patient patient, VisitType type, String clinician, int dayOffset,
                             int hour, int minute, AppointmentStatus status) {
        LocalDate day = LocalDate.now().plusDays(dayOffset);
        if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            day = day.plusDays(dayOffset >= 0 ? 1 : -1);
        }
        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setVisitType(type);
        appointment.setClinician(clinician);
        appointment.setStartTime(LocalDateTime.of(day, LocalTime.of(hour, minute)));
        appointment.setDurationMinutes(type.getDurationMinutes());
        appointment.setStatus(status);
        appointment.setCreatedAt(LocalDateTime.now().minusDays(10));
        return appointment;
    }
}
