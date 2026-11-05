package com.shopsync.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of who is logged in. After a successful login the browser gets a
 * random, unguessable session token in an HttpOnly cookie; every later request
 * sends it back and we look up the user here.
 */
public class SessionManager {

    public static final String COOKIE_NAME = "SHOPSYNC_SESSION";
    public static final Duration LIFETIME = Duration.ofDays(7);

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final class Session {
        final String userId;
        final Instant expires;

        Session(String userId, Instant expires) {
            this.userId = userId;
            this.expires = expires;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String create(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(user.getId(), Instant.now().plus(LIFETIME)));
        return token;
    }

    /** Returns the user id for a valid token, or null if missing/expired. */
    public String resolve(String token) {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (Instant.now().isAfter(s.expires)) {
            sessions.remove(token);
            return null;
        }
        return s.userId;
    }

    public void destroy(String token) {
        if (token != null) sessions.remove(token);
    }
}
