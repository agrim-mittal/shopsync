package com.shopsync.auth;

import com.shopsync.util.JsonSerializable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * A registered customer. The password itself is never stored, only a salted
 * PBKDF2 hash (see {@link PasswordHasher}).
 */
public class User implements JsonSerializable {

    private static final int MAX_RECENT_VIEWS = 12;

    private final String id;
    private final String name;
    private final String email;
    private final String salt;
    private final String passwordHash;
    private final LocalDateTime joinedOn;
    private final LinkedList<String> recentlyViewed = new LinkedList<>();

    public User(String id, String name, String email, String salt, String passwordHash) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.salt = salt;
        this.passwordHash = passwordHash;
        this.joinedOn = LocalDateTime.now();
    }

    /** Remembers a product the user opened; used to personalise recommendations. */
    public synchronized void recordView(String productId) {
        recentlyViewed.remove(productId);
        recentlyViewed.addFirst(productId);
        while (recentlyViewed.size() > MAX_RECENT_VIEWS) recentlyViewed.removeLast();
    }

    public synchronized List<String> getRecentlyViewed() {
        return new ArrayList<>(recentlyViewed);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    String getSalt() { return salt; }
    String getPasswordHash() { return passwordHash; }

    public String getFirstName() {
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    /** Only safe, public fields: never the hash or salt. */
    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("firstName", getFirstName());
        m.put("email", email);
        m.put("joinedOn", joinedOn.toString());
        return m;
    }
}
