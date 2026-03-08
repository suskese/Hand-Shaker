package me.mklv.handshaker.common.protocols;

import me.mklv.handshaker.common.configs.ConfigTypes;

import java.util.Set;

public final class LegacyVersion {
    public interface LogSink {
        void info(String message);
        void warn(String message);
    }

    public enum ClientProfile {
        MODERN,
        HYBRID,
        LEGACY
    }

    private LegacyVersion() {
    }

    public static ClientProfile detectByPayload(String modListPayload, String modListHash, boolean hashMissingOrInvalid) {
        if (hashMissingOrInvalid) {
            return ClientProfile.LEGACY;
        }
        if (modListPayload != null && modListPayload.contains(":")) {
            return ClientProfile.MODERN;
        }
        if (modListHash != null && !modListHash.isBlank()) {
            return ClientProfile.HYBRID;
        }
        return ClientProfile.LEGACY;
    }

    public static ClientProfile detectByMods(Set<String> mods) {
        if (mods == null || mods.isEmpty()) {
            return ClientProfile.LEGACY;
        }
        for (String mod : mods) {
            ConfigTypes.ModEntry entry = ConfigTypes.ModEntry.parse(mod);
            if (entry != null && entry.version() != null && !entry.version().isBlank()) {
                return ClientProfile.MODERN;
            }
        }
        return ClientProfile.HYBRID;
    }

    public static boolean isAllowed(ClientProfile profile,
                                    boolean modernEnabled,
                                    boolean hybridEnabled,
                                    boolean legacyEnabled) {
        return switch (profile) {
            case MODERN -> modernEnabled;
            case HYBRID -> hybridEnabled;
            case LEGACY -> legacyEnabled;
        };
    }

    public static boolean isTrustedHybridJarHash(String jarHash) {
        return ApprovedHashes.isTrusted(jarHash);
    }

    public static void initializeTrustedHybridHashes(Class<?> anchor, LogSink logger, boolean debug) {
        if (logger == null) {
            return;
        }

        ApprovedHashes.initialize(anchor, new ApprovedHashes.LogSink() {
            @Override
            public void info(String message) {
                logger.info(message);
            }

            @Override
            public void warn(String message) {
                logger.warn(message);
            }
        }, debug);
    }
}
