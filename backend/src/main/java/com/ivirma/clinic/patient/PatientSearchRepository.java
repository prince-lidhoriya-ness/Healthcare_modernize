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

    private static final String SEARCH_SQL = "SELECT * FROM patient WHERE lower(first_name) LIKE :pattern"
            + " OR lower(last_name) LIKE :pattern"
            + " OR lower(email) LIKE :pattern"
            + " ORDER BY last_name, first_name";

    @SuppressWarnings("unchecked")
    public List<Patient> search(String term) {
        String t = term.trim().toLowerCase();
        // The term is bound as a parameter, never concatenated into the SQL.
        Query query = entityManager.createNativeQuery(SEARCH_SQL, Patient.class);
        query.setParameter("pattern", "%" + t + "%");
        return query.getResultList();
    }
}
