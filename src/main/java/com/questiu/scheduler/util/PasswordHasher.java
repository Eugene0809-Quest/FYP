package com.questiu.scheduler.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 password hashing for the FYP1 prototype login screen.
 *
 * NOTE: deliberately simple for prototype purposes, using only the JDK's
 * built-in MessageDigest (no external dependency, so it works even though
 * this sandbox can't reach Maven Central). Plain unsalted SHA-256 is NOT
 * how you'd hash passwords in a real system - it's fast to brute-force
 * and has no per-user salt. A production system should use bcrypt or
 * Argon2 instead. Flagged here rather than silently shipping something
 * that looks production-ready.
 */
public class PasswordHasher {

    public static String hash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }

    public static boolean matches(String rawPassword, String storedHash) {
        return hash(rawPassword).equalsIgnoreCase(storedHash);
    }
}
