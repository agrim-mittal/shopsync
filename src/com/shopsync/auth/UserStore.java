package com.shopsync.auth;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/** In-memory customer accounts, keyed by (lower-case) email. Data resets on restart. */
public class UserStore {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final Map<String, User> byEmail = new ConcurrentHashMap<>();
    private final Map<String, User> byId = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1000);

    /** Thrown with a user-friendly message when sign-up or login fails. */
    public static class AuthException extends Exception {
        private static final long serialVersionUID = 1L;

        public AuthException(String message) {
            super(message);
        }
    }

    public synchronized User register(String name, String email, String password) throws AuthException {
        name = name == null ? "" : name.trim();
        email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        if (name.length() < 2) throw new AuthException("Please enter your name.");
        if (!EMAIL.matcher(email).matches()) throw new AuthException("Please enter a valid email address.");
        if (password == null || password.length() < 6) throw new AuthException("Password must be at least 6 characters.");
        if (byEmail.containsKey(email)) throw new AuthException("An account with this email already exists. Try signing in.");

        String salt = PasswordHasher.newSalt();
        User user = new User("U" + nextId.getAndIncrement(), name, email, salt, PasswordHasher.hash(password, salt));
        byEmail.put(email, user);
        byId.put(user.getId(), user);
        return user;
    }

    public User authenticate(String email, String password) throws AuthException {
        User user = email == null ? null : byEmail.get(email.trim().toLowerCase(Locale.ROOT));
        // Same message for "no such user" and "wrong password", so attackers can't discover accounts.
        if (user == null || password == null
                || !PasswordHasher.verify(password, user.getSalt(), user.getPasswordHash())) {
            throw new AuthException("Incorrect email or password.");
        }
        return user;
    }

    public User findById(String id) {
        return id == null ? null : byId.get(id);
    }
}
