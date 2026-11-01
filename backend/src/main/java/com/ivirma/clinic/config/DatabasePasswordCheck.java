package com.ivirma.clinic.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.stereotype.Component;

/**
 * STORY-009 / ADR-003: the database password is supplied at runtime through the {@code DB_PASSWORD}
 * environment variable ({@code application.properties} references it with no default).
 *
 * <p>Spring Boot binds an unresolvable placeholder as its literal text, so without this check a missing
 * variable would only show up later as an opaque authentication failure against PostgreSQL. This check stops
 * start-up as soon as the datasource settings are bound, before any connection is attempted, with
 * {@link MissingDatabasePasswordException}. The value itself is never logged or put in a message.
 */
@Component
public class DatabasePasswordCheck implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSourceProperties properties && !isSupplied(properties.getPassword())) {
            throw new MissingDatabasePasswordException();
        }
        return bean;
    }

    /** False when the value is absent, blank, or still an unresolved {@code ${...}} placeholder. */
    static boolean isSupplied(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        return !(trimmed.startsWith("${") && trimmed.endsWith("}"));
    }
}
