package com.ivirma.clinic.characterization;

import com.ivirma.clinic.appointment.Appointment;
import com.ivirma.clinic.common.AppointmentStatus;
import com.ivirma.clinic.common.Specialty;
import com.ivirma.clinic.common.TreatmentInterest;
import com.ivirma.clinic.common.VisitType;
import com.ivirma.clinic.doctor.Doctor;
import com.ivirma.clinic.patient.Patient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeded, fully literal fixture data shared by the STORY-001 wire-format and golden tests.
 * Names and emails are the synthetic demo people from DemoDataSeeder/DoctorSeeder (fictional).
 */
final class Fixtures {

    /** Frozen server clock for golden recordings: Thu 2026-10-29 10:00:00. */
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 10, 0, 0);

    private Fixtures() {
    }

    static Patient laura() {
        return patient(1L, "Laura", "Gomez", "1989-04-12", "laura.gomez@example.com", "+34 600 000 001",
                TreatmentInterest.IVF, LocalDateTime.of(2026, 10, 1, 9, 0));
    }

    static Patient sofia() {
        return patient(2L, "Sofia", "Romano", "1992-09-03", "sofia.romano@example.com", "+34 600 000 002",
                TreatmentInterest.EGG_FREEZING, LocalDateTime.of(2026, 10, 25, 9, 0));
    }

    static Patient patient(long id, String first, String last, String dob, String email, String phone,
                           TreatmentInterest interest, LocalDateTime createdAt) {
        Patient p = new Patient();
        p.setId(id);
        p.setFirstName(first);
        p.setLastName(last);
        p.setDateOfBirth(LocalDate.parse(dob));
        p.setEmail(email);
        p.setPhone(phone);
        p.setTreatmentInterest(interest);
        p.setCreatedAt(createdAt);
        return p;
    }

    /** The four seeded doctors, in the repository's last-name order. */
    static List<Doctor> doctors() {
        List<Doctor> list = new ArrayList<Doctor>();
        list.add(doctor(2L, "Marco", "Bianchi", Specialty.GYNAECOLOGY));
        list.add(doctor(3L, "Sarah", "Collins", Specialty.REPRODUCTIVE_MEDICINE));
        list.add(doctor(4L, "Priya", "Nair", Specialty.REPRODUCTIVE_IMAGING));
        list.add(doctor(1L, "Elena", "Ruiz", Specialty.REPRODUCTIVE_MEDICINE));
        return list;
    }

    static Doctor doctor(long id, String first, String last, Specialty specialty) {
        Doctor d = new Doctor();
        d.setId(id);
        d.setFirstName(first);
        d.setLastName(last);
        d.setName(Doctor.displayName(first, last));
        d.setSpecialty(specialty);
        d.setEmail(first.toLowerCase() + "." + last.toLowerCase() + "@clinic.example.com");
        d.setCreatedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        return d;
    }

    static Appointment appointment(long id, Patient patient, VisitType type, String clinician, LocalDateTime start,
                                   AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatient(patient);
        a.setVisitType(type);
        a.setClinician(clinician);
        a.setStartTime(start);
        a.setDurationMinutes(type.getDurationMinutes());
        a.setStatus(status);
        a.setCreatedAt(LocalDateTime.of(2026, 10, 19, 12, 0));
        return a;
    }
}
