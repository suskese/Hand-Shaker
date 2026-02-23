package me.mklv.handshaker.common.commands;

import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.ClientInfo;

public final class ModFingerprintRegistrar {
    private ModFingerprintRegistrar() {
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
        String resolvedHash = CommandModUtil.normalizeHash(requested.hash());
        if (resolvedHash == null) {
            resolvedHash = CommandModUtil.resolveHashFromConnectedClients(
                connectedClients,
                requested.modId(),
                requestedVersion
            );
        }

        db.registerModFingerprint(requested.modId(), requestedVersion, resolvedHash);
    }
}
