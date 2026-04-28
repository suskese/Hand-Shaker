package me.mklv.handshaker.common.protocols;

import me.mklv.handshaker.common.configs.ConfigTypes;

import java.util.Set;

public final class LegacyVersion {
    public interface LogSink {
        void info(String message);
        void warn(String message);
    }

    public enum ModListFormat {
        MODERN,
        HYBRID,
        LEGACY
    }

    private LegacyVersion() {
    }

    public static ModListFormat detectByPayload(String modListPayload, String modListHash, boolean hashMissingOrInvalid) {
        if (hashMissingOrInvalid) {
            return ModListFormat.LEGACY;
        }
        if (modListPayload != null && modListPayload.contains(":")) {
            return ModListFormat.MODERN;
        }
        if (modListHash != null && !modListHash.isBlank()) {
            return ModListFormat.HYBRID;
        }
        return ModListFormat.LEGACY;
    }

    public static ModListFormat detectByMods(Set<String> mods) {
        if (mods == null || mods.isEmpty()) {
            return ModListFormat.LEGACY;
        }
        for (String mod : mods) {
            ConfigTypes.ModEntry entry = ConfigTypes.ModEntry.parse(mod);
            if (entry != null && entry.version() != null && !entry.version().isBlank()) {
                return ModListFormat.MODERN;
            }
        }
        return ModListFormat.HYBRID;
    }

    public static boolean isAllowed(ModListFormat modListFormat,
                                    boolean modernEnabled,
                                    boolean hybridEnabled,
                                    boolean legacyEnabled) {
        return switch (modListFormat) {
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
