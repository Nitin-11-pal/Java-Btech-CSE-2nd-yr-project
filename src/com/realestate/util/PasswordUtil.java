package com.realestate.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Password hashing utility using standard JDK PBKDF2WithHmacSHA256 (120,000 iterations,
 * 16-byte cryptographic random salt) and constant-time comparison via MessageDigest.isEqual.
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {
        // Utility class
    }

    /**
     * Generates a random 16-byte cryptographic salt encoded as Base64.
     *
     * @return Base64-encoded 16-byte salt
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Computes a PBKDF2WithHmacSHA256 hash of the password and Base64-encoded salt.
     *
     * @param plainPassword plain-text password
     * @param base64Salt    Base64-encoded salt
     * @return Base64-encoded derived key hash
     */
    public static String hashPassword(String plainPassword, String base64Salt) {
        if (plainPassword == null || base64Salt == null) {
            throw new IllegalArgumentException("Password and salt must not be null.");
        }
        byte[] saltBytes = Base64.getDecoder().decode(base64Salt);
        PBEKeySpec spec = new PBEKeySpec(
                plainPassword.toCharArray(),
                saltBytes,
                ITERATIONS,
                KEY_LENGTH_BITS
        );
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hashBytes = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to hash password with " + ALGORITHM, e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Verifies a plain-text password against a stored salt and hash using constant-time comparison.
     *
     * @param plainPassword      candidate plain-text password
     * @param base64Salt         stored Base64 salt
     * @param expectedBase64Hash stored Base64 password hash
     * @return true if the password matches
     */
    public static boolean verifyPassword(String plainPassword, String base64Salt, String expectedBase64Hash) {
        if (plainPassword == null || base64Salt == null || expectedBase64Hash == null) {
            return false;
        }
        String computedBase64Hash = hashPassword(plainPassword, base64Salt);
        byte[] computedBytes = Base64.getDecoder().decode(computedBase64Hash);
        byte[] expectedBytes = Base64.getDecoder().decode(expectedBase64Hash);
        return MessageDigest.isEqual(computedBytes, expectedBytes);
    }
}
