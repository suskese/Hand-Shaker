package me.mklv.handshaker.common.utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

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

    public static Optional<String> sha256FileHex(Path filePath) {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return Optional.empty();
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return Optional.of(toHex(digest.digest()));
        } catch (Exception e) {
            return Optional.empty();
        }
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
