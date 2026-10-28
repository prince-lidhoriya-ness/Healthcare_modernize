package com.ivirma.clinic.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Fixed list of fictional clinicians used in the demo.
 */
public final class Clinicians {

    private static final List<String> NAMES = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(
            "Dr. Elena Ruiz",
            "Dr. Marco Bianchi",
            "Dr. Sarah Collins",
            "Dr. Priya Nair"
    )));

    private Clinicians() {
    }

    public static List<String> all() {
        return NAMES;
    }

    public static boolean isKnown(String name) {
        return name != null && NAMES.contains(name);
    }
}
