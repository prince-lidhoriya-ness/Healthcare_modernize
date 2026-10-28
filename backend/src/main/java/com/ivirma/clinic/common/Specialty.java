package com.ivirma.clinic.common;

/**
 * A doctor's area of practice in the clinic.
 */
public enum Specialty {
    REPRODUCTIVE_MEDICINE("Reproductive medicine"),
    GYNAECOLOGY("Gynaecology"),
    EMBRYOLOGY("Embryology"),
    ANDROLOGY("Andrology"),
    REPRODUCTIVE_IMAGING("Reproductive imaging");

    private final String label;

    Specialty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
