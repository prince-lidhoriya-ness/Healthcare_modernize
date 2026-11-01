package com.ivirma.clinic.patient;

import com.ivirma.clinic.common.ConflictException;
import com.ivirma.clinic.common.NotFoundException;
import org.apache.commons.text.WordUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PatientService {

    /** STORY-024 (SEC-007): log record ids, counts and the action only, never personal data or raw request input. */
    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final PatientSearchRepository patientSearchRepository;

    public PatientService(PatientRepository patientRepository, PatientSearchRepository patientSearchRepository) {
        this.patientRepository = patientRepository;
        this.patientSearchRepository = patientSearchRepository;
    }

    @Transactional
    public Patient register(PatientRequest request) {
        log.info("Registering new patient");

        if (patientRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ConflictException("A patient with email " + request.getEmail() + " already exists");
        }

        Patient patient = new Patient();
        patient.setFirstName(WordUtils.capitalizeFully(request.getFirstName().trim()));
        patient.setLastName(WordUtils.capitalizeFully(request.getLastName().trim()));
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setEmail(request.getEmail().trim().toLowerCase());
        patient.setPhone(request.getPhone());
        patient.setTreatmentInterest(request.getTreatmentInterest());
        patient.setCreatedAt(LocalDateTime.now());

        Patient saved = patientRepository.save(patient);
        log.info("Patient saved with id {}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Patient> list(String search) {
        if (search == null || search.trim().isEmpty()) {
            return patientRepository.findAllByOrderByLastNameAscFirstNameAsc();
        }
        List<Patient> matches = patientSearchRepository.search(search);
        log.info("Patient search returned {} result(s)", matches.size());
        return matches;
    }

    @Transactional(readOnly = true)
    public Patient get(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Patient " + id + " not found"));
    }

    @Transactional
    public Patient update(Long id, PatientRequest request) {
        Patient patient = get(id);
        log.info("Updating patient {}", id);

        if (request.getEmail() != null && patientRepository.existsByEmailIgnoreCaseAndIdNot(request.getEmail(), id)) {
            throw new ConflictException("A patient with email " + request.getEmail() + " already exists");
        }

        patient.setFirstName(request.getFirstName());
        patient.setLastName(request.getLastName());
        patient.setDateOfBirth(request.getDateOfBirth());
        patient.setEmail(request.getEmail());
        patient.setPhone(request.getPhone());
        patient.setTreatmentInterest(request.getTreatmentInterest());
        return patientRepository.save(patient);
    }
}
