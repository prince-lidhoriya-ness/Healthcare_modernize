package com.ivirma.clinic.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    long countByCreatedAtAfter(LocalDateTime since);

    List<Patient> findAllByOrderByLastNameAscFirstNameAsc();
}
