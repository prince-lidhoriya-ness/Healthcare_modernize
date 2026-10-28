package com.ivirma.clinic.common;

/**
 * The area of fertility care a patient is interested in.
 * This is an administrative label for routing, not a diagnosis.
 */
public enum TreatmentInterest {
    FERTILITY_ASSESSMENT("Fertility assessment"),
    IVF("IVF"),
    IUI("IUI"),
    EGG_FREEZING("Egg freezing"),
    EGG_DONATION("Egg donation");

    private final String label;

    TreatmentInterest(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
