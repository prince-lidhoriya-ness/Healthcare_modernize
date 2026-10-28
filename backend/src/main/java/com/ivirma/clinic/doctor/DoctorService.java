package com.ivirma.clinic.doctor;

import com.ivirma.clinic.common.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DoctorService {

    private static final Logger log = LoggerFactory.getLogger(DoctorService.class);

    private final DoctorRepository doctorRepository;

    public DoctorService(DoctorRepository doctorRepository) {
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    public Doctor register(DoctorRequest request) {
        String firstName = request.getFirstName().trim();
        String lastName = request.getLastName().trim();
        String name = Doctor.displayName(firstName, lastName);
        String email = request.getEmail().trim().toLowerCase();

        if (doctorRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException(name + " is already registered");
        }
        if (doctorRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("A doctor with email " + email + " already exists");
        }

        Doctor doctor = new Doctor();
        doctor.setFirstName(firstName);
        doctor.setLastName(lastName);
        doctor.setName(name);
        doctor.setSpecialty(request.getSpecialty());
        doctor.setEmail(email);
        doctor.setPhone(request.getPhone());
        doctor.setCreatedAt(LocalDateTime.now());
        Doctor saved = doctorRepository.save(doctor);
        log.info("Doctor registered with id {}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Doctor> list() {
        return doctorRepository.findAllByOrderByLastNameAscFirstNameAsc();
    }

    /** Names for the booking dropdowns, sorted by last name. */
    @Transactional(readOnly = true)
    public List<String> names() {
        List<String> names = new ArrayList<String>();
        for (Doctor d : list()) {
            names.add(d.getName());
        }
        return names;
    }

    @Transactional(readOnly = true)
    public boolean isKnown(String name) {
        return name != null && doctorRepository.existsByName(name);
    }
}
