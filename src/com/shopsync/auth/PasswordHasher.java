package com.shopsync.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes passwords with PBKDF2-HMAC-SHA256 (built into the JDK).
 *
 * Why not just SHA-256? A plain hash is fast, so attackers can try billions of
 * guesses per second. PBKDF2 repeats the hash thousands of times and mixes in a
 * random per-user salt, so identical passwords produce different hashes and
 * guessing becomes very slow.
 */
public final class PasswordHasher {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() { }

    public static String newSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hash(String password, String salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(salt),
                    ITERATIONS, KEY_BITS);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.getEncoder().encodeToString(hash);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 not available", e);
        }
    }

    /** Constant-time comparison so response timing doesn't leak how much of the hash matched. */
    public static boolean verify(String password, String salt, String expectedHash) {
        byte[] a = hash(password, salt).getBytes();
        byte[] b = expectedHash.getBytes();
        return MessageDigest.isEqual(a, b);
    }
}
