package com.ivirma.clinic.auth;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues random bearer tokens and keeps the active sessions in memory.
 * Restarting the backend signs everyone out, which is fine for the demo.
 */
@Service
public class TokenService {

    static final Duration SESSION_LENGTH = Duration.ofHours(8);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();

    public String issue(AuthenticatedUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user, Instant.now().plus(SESSION_LENGTH)));
        return token;
    }

    public Optional<AuthenticatedUser> resolve(String token) {
        if (token == null) {
            return Optional.empty();
        }
        Session session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session.user);
    }

    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private static final class Session {
        private final AuthenticatedUser user;
        private final Instant expiresAt;

        private Session(AuthenticatedUser user, Instant expiresAt) {
            this.user = user;
            this.expiresAt = expiresAt;
        }
    }
}
