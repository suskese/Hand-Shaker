package me.mklv.handshaker.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtils {
    private HashUtils() {
    }

    public static byte[] sha256Bytes(String input) {
        if (input == null) {
            return new byte[0];
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            return new byte[0];
        }
    }

    public static String sha256Hex(String input) {
        byte[] hash = sha256Bytes(input);
        if (hash.length == 0) {
            return "";
        }
        return toHex(hash);
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
