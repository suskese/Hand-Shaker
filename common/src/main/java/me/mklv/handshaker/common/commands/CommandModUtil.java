package me.mklv.handshaker.common.commands;

import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.ClientInfo;

import java.util.Locale;

public final class CommandModUtil {
    private CommandModUtil() {
    }

    public static String defaultActionForMode(String mode) {
        return "blacklisted".equalsIgnoreCase(mode) ? "kick" : "none";
    }

    public static boolean matchesVersion(String requestedVersion, String candidateVersion) {
        if (requestedVersion == null || requestedVersion.isBlank() || "null".equalsIgnoreCase(requestedVersion)) {
            return true;
        }
        return requestedVersion.equalsIgnoreCase(candidateVersion);
    }

    public static String normalizeHash(String hash) {
        if (hash == null || hash.isBlank() || "null".equalsIgnoreCase(hash)) {
            return null;
        }
        return hash.toLowerCase(Locale.ROOT);
    }

    public static String resolveHashFromConnectedClients(Iterable<ClientInfo> clients, String modId, String modVersion) {
        if (clients == null || modId == null || modId.isBlank()) {
            return null;
        }

        String normalizedModId = modId.trim().toLowerCase(Locale.ROOT);
        if (normalizedModId.isEmpty()) {
            return null;
        }

        for (ClientInfo info : clients) {
            if (info == null || info.mods() == null) {
                continue;
            }

            for (String clientToken : info.mods()) {
                ModEntry candidate = ModEntry.parse(clientToken);
                if (candidate == null || !candidate.modId().equals(normalizedModId)) {
                    continue;
                }
                if (!matchesVersion(modVersion, candidate.version())) {
                    continue;
                }
                String candidateHash = normalizeHash(candidate.hash());
                if (candidateHash != null) {
                    return candidateHash;
                }
            }
        }

        return null;
    }

    public static void registerFromCommand(
        String modToken,
        PlayerHistoryDatabase db,
        boolean isHashingEnabled,
        boolean isVersioningEnabled,
        Iterable<ClientInfo> connectedClients
    ) {
        if (!isHashingEnabled || db == null) {
            return;
        }

        ModEntry requested = ModEntry.parse(modToken);
        if (requested == null) {
            return;
        }

        String requestedVersion = isVersioningEnabled ? requested.version() : null;
        String resolvedHash = normalizeHash(requested.hash());
        if (resolvedHash == null) {
            resolvedHash = resolveHashFromConnectedClients(
                connectedClients,
                requested.modId(),
                requestedVersion
            );
        }

        db.registerModFingerprint(requested.modId(), requestedVersion, resolvedHash);
    }
}
