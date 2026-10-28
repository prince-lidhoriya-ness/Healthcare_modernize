package com.ivirma.clinic.config;

import com.ivirma.clinic.common.Specialty;
import com.ivirma.clinic.doctor.Doctor;
import com.ivirma.clinic.doctor.DoctorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the clinic's four fictional doctors when the doctor table is empty.
 * These were a fixed list in code before doctors could be registered, and existing
 * appointments refer to them by name, so they are always loaded (even with demo data off).
 */
@Component
@Order(0)
public class DoctorSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DoctorSeeder.class);

    private final DoctorRepository doctorRepository;

    public DoctorSeeder(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (doctorRepository.count() > 0) {
            return;
        }
        List<Doctor> doctors = new ArrayList<Doctor>();
        doctors.add(doctor("Elena", "Ruiz", Specialty.REPRODUCTIVE_MEDICINE));
        doctors.add(doctor("Marco", "Bianchi", Specialty.GYNAECOLOGY));
        doctors.add(doctor("Sarah", "Collins", Specialty.REPRODUCTIVE_MEDICINE));
        doctors.add(doctor("Priya", "Nair", Specialty.REPRODUCTIVE_IMAGING));
        doctorRepository.saveAll(doctors);
        log.info("Seeded {} doctors", doctors.size());
    }

    private Doctor doctor(String first, String last, Specialty specialty) {
        Doctor doctor = new Doctor();
        doctor.setFirstName(first);
        doctor.setLastName(last);
        doctor.setName(Doctor.displayName(first, last));
        doctor.setSpecialty(specialty);
        doctor.setEmail(first.toLowerCase() + "." + last.toLowerCase() + "@clinic.example.com");
        doctor.setCreatedAt(LocalDateTime.now());
        return doctor;
    }
}
