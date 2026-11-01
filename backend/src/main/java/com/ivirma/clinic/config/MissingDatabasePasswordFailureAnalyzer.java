package com.ivirma.clinic.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * STORY-009: turns {@link MissingDatabasePasswordException} into Spring Boot's short
 * "APPLICATION FAILED TO START" report (description + action) instead of a bean-creation stack trace.
 * Registered in {@code META-INF/spring.factories}.
 */
public class MissingDatabasePasswordFailureAnalyzer extends AbstractFailureAnalyzer<MissingDatabasePasswordException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, MissingDatabasePasswordException cause) {
        return new FailureAnalysis(
                "The database password is not configured. The backend reads it from the DB_PASSWORD environment "
                        + "variable, which is missing or empty; there is no default in the repository.",
                "Supply DB_PASSWORD at runtime. Docker Compose: copy .env.example to .env next to "
                        + "docker-compose.yml and set DB_PASSWORD there (.env is git-ignored). Jenkins: create the "
                        + "\"Secret text\" credential 'ivirma-db-password'. Plain java -jar: export DB_PASSWORD first.",
                cause);
    }
}
