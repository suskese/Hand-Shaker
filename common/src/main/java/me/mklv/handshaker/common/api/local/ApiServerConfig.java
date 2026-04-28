package me.mklv.handshaker.common.api.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public record ApiServerConfig(
    boolean enabled,
    int port,
    String apiKey
) {
    public ApiServerConfig {
        port = Math.max(1, Math.min(port, 65535));
        apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public static ApiServerConfig disabled() {
        return new ApiServerConfig(false, 8888, "");
    }

    public boolean requiresAuth() {
        return !apiKey.isBlank();
    }

    public boolean authMatches(String providedKey) {
        if (!requiresAuth()) {
            return true;
        }
        if (providedKey == null) {
            return false;
        }
        byte[] expected = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] provided = providedKey.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided);
    }
}
