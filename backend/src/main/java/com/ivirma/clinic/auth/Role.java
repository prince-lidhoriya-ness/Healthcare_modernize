package com.ivirma.clinic.auth;

/**
 * Which portal an account signs in to.
 * ADMIN = clinic staff (patient coordinator), CUSTOMER = a patient using the customer portal.
 */
public enum Role {
    ADMIN,
    CUSTOMER
}
