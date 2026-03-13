package me.mklv.handshaker.common.api.local;

import java.util.Objects;

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
        return !requiresAuth() || Objects.equals(apiKey, providedKey);
    }
}
