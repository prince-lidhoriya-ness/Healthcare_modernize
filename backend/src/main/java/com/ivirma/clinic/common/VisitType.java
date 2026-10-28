package com.ivirma.clinic.common;

/**
 * Fertility clinic visit types. Each visit type has a fixed length,
 * which is used to detect scheduling conflicts.
 */
public enum VisitType {
    INITIAL_CONSULTATION("Initial consultation", 45),
    ULTRASOUND_MONITORING("Ultrasound monitoring", 20),
    BLOOD_TEST("Blood test", 15),
    EGG_RETRIEVAL("Egg retrieval", 60),
    EMBRYO_TRANSFER("Embryo transfer", 30),
    FOLLOW_UP("Follow-up", 30);

    private final String label;
    private final int durationMinutes;

    VisitType(String label, int durationMinutes) {
        this.label = label;
        this.durationMinutes = durationMinutes;
    }

    public String getLabel() {
        return label;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }
}
