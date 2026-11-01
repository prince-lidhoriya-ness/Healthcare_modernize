package com.ivirma.clinic.testsupport;

import com.ivirma.clinic.common.TreatmentInterest;
import com.ivirma.clinic.patient.Patient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STORY-024: one synthetic patient (allowlisted example.org email, made-up name and phone) whose personal and
 * health fields must never reach a log line. Distinctive values, so a leak cannot be a coincidental match.
 */
public final class SyntheticPatient {

    public static final long ID = 4711L;
    public static final String FIRST_NAME = "Marisol";
    public static final String LAST_NAME = "Quintanilla";
    public static final LocalDate BIRTH_DATE = LocalDate.of(1987, 3, 21);
    public static final String MAIL = "marisol.quintanilla@example.org";
    public static final String TELEPHONE = "+34 600 000 417";
    public static final TreatmentInterest INTEREST = TreatmentInterest.EGG_FREEZING;

    private SyntheticPatient() {
    }

    /** The stored patient, every field set. */
    public static Patient entity() {
        Patient p = new Patient();
        p.setId(ID);
        p.setFirstName(FIRST_NAME);
        p.setLastName(LAST_NAME);
        p.setDateOfBirth(BIRTH_DATE);
        p.setEmail(MAIL);
        p.setPhone(TELEPHONE);
        p.setTreatmentInterest(INTEREST);
        p.setCreatedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        return p;
    }

    /** Every personal or health field of {@link #entity()} by label, for {@link LogCapture#assertNoLineCarries}. */
    public static Map<String, Object> personalData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("first name", FIRST_NAME);
        data.put("last name", LAST_NAME);
        data.put("date of birth", BIRTH_DATE);
        data.put("email address", MAIL);
        data.put("phone number", TELEPHONE);
        data.put("treatment interest", INTEREST);
        data.put("treatment interest label", INTEREST.getLabel());
        return data;
    }
}
