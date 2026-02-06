package me.mklv.handshaker.fabric.server.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility methods for cryptographic operations.
 */
public class CryptoUtils {
    /**
     * Hashes a string using SHA-256.
     * @param input The string to hash
     * @return The byte array of the hash, or empty array on error
     */
    public static byte[] hashStringToBytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            return new byte[0];
        }
    }
}
