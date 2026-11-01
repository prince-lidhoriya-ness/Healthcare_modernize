package com.ivirma.clinic.config;

/**
 * STORY-009: start-up stopped because the database password was not supplied at runtime.
 * The message names the environment variable only, never a value.
 */
public class MissingDatabasePasswordException extends IllegalStateException {

    public MissingDatabasePasswordException() {
        super("The database password is not configured: the DB_PASSWORD environment variable is missing or empty");
    }
}
