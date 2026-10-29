package com.ivirma.clinic.testsupport;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only credentials generated at runtime, so no password lives as a literal in the test sources or
 * in recorded fixtures (gate 2, STORY-001). Every call returns a fresh random value for this JVM run and
 * registers it under a label; {@link #redact(String)} replaces each registered value with {@code <LABEL>},
 * which keeps recordings such as the golden files reproducible and free of secrets.
 *
 * <p>Values use the URL-safe base64 alphabet ({@code A-Z a-z 0-9 - _}): ASCII, no whitespace, no JSON or
 * regex metacharacters, so they can be embedded in request bodies and compared byte for byte.
 */
public final class TestSecrets {

    /** Long enough for every password rule in the app (RULE-028: 8 to 100 characters). */
    public static final int DEFAULT_LENGTH = 24;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, String> PLACEHOLDER_BY_VALUE = new ConcurrentHashMap<String, String>();

    private TestSecrets() {
    }

    /** A fresh random password of {@link #DEFAULT_LENGTH} characters, redacted as {@code <label>}. */
    public static String password(String label) {
        return password(label, DEFAULT_LENGTH);
    }

    /** A fresh random password of exactly {@code length} ASCII characters, redacted as {@code <label>}. */
    public static String password(String label, int length) {
        if (label == null || !label.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("label must be UPPER_SNAKE_CASE: " + label);
        }
        if (length < 8) {
            throw new IllegalArgumentException("length must be at least 8 so values cannot collide: " + length);
        }
        byte[] bytes = new byte[(length * 3 + 3) / 4];
        RANDOM.nextBytes(bytes);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, length);
        PLACEHOLDER_BY_VALUE.put(value, "<" + label + ">");
        return value;
    }

    /** Replaces every value issued by this class during the run with its {@code <LABEL>} placeholder. */
    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = text;
        for (Map.Entry<String, String> secret : PLACEHOLDER_BY_VALUE.entrySet()) {
            out = out.replace(secret.getKey(), secret.getValue());
        }
        return out;
    }
}
