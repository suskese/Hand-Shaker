package me.mklv.handshaker.common.loader;

import java.util.UUID;

public final class CommonClientHandshakeOrchestrator {
    public interface Logger {
        void info(String format, Object... args);

        void warn(String message);
    }

    public interface Sender {
        void sendModList(String transportPayload, String modListHash, String nonce);

        void sendIntegrity(byte[] signature, String jarHash, String nonce);
    }

    public interface Availability {
        boolean isConnectionReady();
    }

    public interface PayloadProvider {
        CommonClientHashPayloadService.ModListData getModListData();

        CommonClientHashPayloadService.IntegrityData getIntegrityData();
    }

    public void onJoin(Availability availability, PayloadProvider payloads, Sender sender, Logger logger) {
        if (!availability.isConnectionReady()) {
            return;
        }

        CommonClientHashPayloadService.ModListData modList = payloads.getModListData();
        String modListNonce = nextNonce();
        sender.sendModList(modList.transportPayload(), modList.modListHash(), modListNonce);
        logger.info(
            "Sent mod list ({} chars, hash: {}) with nonce: {}",
            modList.transportPayload().length(),
            abbreviateHash(modList.modListHash()),
            modListNonce
        );

        CommonClientHashPayloadService.IntegrityData integrity = payloads.getIntegrityData();
        String integrityNonce = nextNonce();
        sender.sendIntegrity(integrity.signature(), integrity.jarHash(), integrityNonce);
        if (!integrity.jarHash().isBlank()) {
            logger.info(
                "Sent detached integrity proof ({} bytes) with content hash {} and nonce: {}",
                integrity.signature().length,
                abbreviateHash(integrity.jarHash()),
                integrityNonce
            );
        } else {
            logger.warn("Could not build runtime integrity proof. Sending empty payload.");
        }
    }

    private String nextNonce() {
        return UUID.randomUUID().toString();
    }

    private String abbreviateHash(String hash) {
        if (hash == null || hash.length() < 8) {
            return hash == null ? "" : hash;
        }
        return hash.substring(0, 8);
    }
}