package com.ivirma.clinic.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;

/**
 * STORY-001 characterization of {@link TokenService}: RULE-040 (256-bit token, fixed 8-hour
 * lifetime, expired tokens removed when presented) and RULE-041 (logout revokes only the
 * presented token). {@code Instant.now()} is frozen with a static mock to cross the expiry instant.
 */
@DisplayName("STORY-001 TokenService characterization")
class TokenServiceCharacterizationTest {

    private static final AuthenticatedUser LAURA =
            new AuthenticatedUser(5L, "laura.gomez@example.com", Role.CUSTOMER, "Laura Gomez", 7L);

    private static MockedStatic<Instant> frozenAt(Instant[] now) {
        MockedStatic<Instant> clock = mockStatic(Instant.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS).strictness(Strictness.LENIENT));
        clock.when(Instant::now).thenAnswer(inv -> now[0]);
        return clock;
    }

    @Test
    @DisplayName("RULE-040 STORY-001 a token is 32 random bytes, URL-safe base64 without padding (43 chars)")
    void RULE_040_STORY001_tokenIs256BitUrlSafeBase64() {
        TokenService tokens = new TokenService();

        String t1 = tokens.issue(LAURA);
        String t2 = tokens.issue(LAURA);

        assertThat(t1).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(t1)).hasSize(32);
        assertThat(t2).isNotEqualTo(t1);
    }

    @Test
    @DisplayName("RULE-040 STORY-001 the session length is a fixed 8 hours")
    void RULE_040_STORY001_sessionLengthIsEightHours() {
        assertThat(TokenService.SESSION_LENGTH).isEqualTo(Duration.ofHours(8));
    }

    @Test
    @DisplayName("RULE-040 STORY-001 issued 09:00:00Z: valid at 17:00:00Z exactly, gone at 17:00:01Z and removed")
    void RULE_040_STORY001_tokenExpiresAfterEightHoursAndIsRemoved() {
        Instant[] now = {Instant.parse("2026-10-29T09:00:00Z")};
        try (MockedStatic<Instant> ignored = frozenAt(now)) {
            TokenService tokens = new TokenService();
            String token = tokens.issue(LAURA);

            now[0] = Instant.parse("2026-10-29T17:00:00Z");
            assertThat(tokens.resolve(token)).contains(LAURA); // valid at the exact expiry instant

            now[0] = Instant.parse("2026-10-29T17:00:01Z");
            assertThat(tokens.resolve(token)).isEmpty();

            now[0] = Instant.parse("2026-10-29T10:00:00Z"); // even if the clock went back, it was removed
            assertThat(tokens.resolve(token)).isEmpty();
        }
    }

    @Test
    @DisplayName("RULE-040 STORY-001 the lifetime is not sliding: using the token at 16:59 does not extend it")
    void RULE_040_STORY001_lifetimeIsNotSliding() {
        Instant[] now = {Instant.parse("2026-10-29T09:00:00Z")};
        try (MockedStatic<Instant> ignored = frozenAt(now)) {
            TokenService tokens = new TokenService();
            String token = tokens.issue(LAURA);

            now[0] = Instant.parse("2026-10-29T16:59:00Z");
            assertThat(tokens.resolve(token)).isPresent();

            now[0] = Instant.parse("2026-10-29T17:00:01Z");
            assertThat(tokens.resolve(token)).isEmpty();
        }
    }

    @Test
    @DisplayName("RULE-040 STORY-001 unknown and null tokens resolve to nobody")
    void RULE_040_STORY001_unknownOrNullTokenIsAnonymous() {
        TokenService tokens = new TokenService();

        assertThat(tokens.resolve(null)).isEmpty();
        assertThat(tokens.resolve("not-a-token")).isEmpty();
    }

    @Test
    @DisplayName("RULE-041 STORY-001 revoking T1 leaves T2 of the same user valid; revoke(null) is a no-op")
    void RULE_041_STORY001_logoutRevokesOnlyThePresentedToken() {
        TokenService tokens = new TokenService();
        String t1 = tokens.issue(LAURA);
        String t2 = tokens.issue(LAURA);

        tokens.revoke(t1);
        tokens.revoke(null);

        assertThat(tokens.resolve(t1)).isEmpty();
        assertThat(tokens.resolve(t2)).contains(LAURA);
    }
}
