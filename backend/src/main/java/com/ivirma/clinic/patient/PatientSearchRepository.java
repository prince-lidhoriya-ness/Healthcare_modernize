package com.ivirma.clinic.patient;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Free-text patient search by name or email.
 */
@Repository
public class PatientSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Patient> search(String term) {
        String t = term.trim().toLowerCase();
        String sql = "SELECT * FROM patient WHERE lower(first_name) LIKE '%" + t + "%'"
                + " OR lower(last_name) LIKE '%" + t + "%'"
                + " OR lower(email) LIKE '%" + t + "%'"
                + " ORDER BY last_name, first_name";
        Query query = entityManager.createNativeQuery(sql, Patient.class);
        return query.getResultList();
    }
}
