package me.mklv.handshaker.common.configs;

public final class StandardMessages {
    private StandardMessages() {
    }

    public static final String KEY_KICK = "kick";
    public static final String KEY_NO_HANDSHAKE = "no-handshake";
    public static final String KEY_MISSING_WHITELIST = "missing-whitelist";
    public static final String KEY_INVALID_SIGNATURE = "invalid-signature";
    public static final String KEY_OUTDATED_CLIENT = "outdated-client";
    public static final String KEY_HANDSHAKE_CORRUPTED = "handshake-corrupted";
    public static final String KEY_HANDSHAKE_EMPTY_MOD_LIST = "handshake-empty-mod-list";
    public static final String KEY_HANDSHAKE_HASH_MISMATCH = "handshake-hash-mismatch";
    public static final String KEY_HANDSHAKE_MISSING_HASH = "handshake-missing-hash";
    public static final String KEY_HANDSHAKE_MISSING_JAR_HASH = "handshake-missing-jar-hash";
    public static final String KEY_HANDSHAKE_MISSING_NONCE = "handshake-missing-nonce";
    public static final String KEY_HANDSHAKE_MISSING_SIGNATURE = "handshake-missing-signature";
    public static final String KEY_HANDSHAKE_REPLAY = "handshake-replay";
    public static final String KEY_VELTON_FAILED = "velton-verification-failed";

    public static final String DEFAULT_KICK_MESSAGE =
        "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    public static final String DEFAULT_NO_HANDSHAKE_MESSAGE =
        "To connect to this server please download 'Hand-shaker' mod.";
    public static final String DEFAULT_MISSING_WHITELIST_MESSAGE =
        "You are missing required mods: {mod}. Please install them to join this server.";
    public static final String DEFAULT_INVALID_SIGNATURE_MESSAGE =
        "Invalid client signature. Please use the official client.";
    public static final String DEFAULT_OUTDATED_CLIENT_MESSAGE =
        "Your HandShaker client is outdated. Please update to the latest version.";

    public static final String HANDSHAKE_CORRUPTED = "Corrupted handshake data";
    public static final String HANDSHAKE_EMPTY_MOD_LIST = "Invalid handshake: empty mod list";
    public static final String HANDSHAKE_HASH_MISMATCH = "Invalid handshake: hash mismatch";
    public static final String HANDSHAKE_MISSING_HASH = "Invalid handshake: missing hash";
    public static final String HANDSHAKE_MISSING_JAR_HASH = "Invalid handshake: missing jar hash";
    public static final String HANDSHAKE_MISSING_NONCE = "Invalid handshake: missing nonce";
    public static final String HANDSHAKE_MISSING_SIGNATURE = "Invalid handshake: missing signature";
    public static final String HANDSHAKE_REPLAY = "Replay attack detected";

    public static final String VELTON_VERIFICATION_FAILED = "Anti-cheat verification failed";
}
