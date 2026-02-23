package me.mklv.handshaker.common.protocols;

import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.HashUtils;
import me.mklv.handshaker.common.utils.SignatureVerifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified payload validation system for all platforms (Paper, Fabric, NeoForge).
 * Handles validation logic for mod lists, integrity checks, and Velton payloads.
 * Provides callback interface for platform-specific actions.
 */
public class PayloadValidation {
    private final PayloadValidationCallbacks callbacks;
    private final SignatureVerifier signatureVerifier;
    private final Set<String> usedNonces = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ClientInfo> clients;
    private boolean debugMode = false;

    public PayloadValidation(PayloadValidationCallbacks callbacks, SignatureVerifier signatureVerifier, 
                            Map<UUID, ClientInfo> clients) {
        this.callbacks = callbacks;
        this.signatureVerifier = signatureVerifier;
        this.clients = clients;
    }

    public void setDebugMode(boolean debug) {
        this.debugMode = debug;
    }

    /**
     * Validates a mod list payload and updates client info.
     * Checks: nonce validity, hash, replay attacks, mod list parsing.
     */
    public ValidationResult validateModList(UUID playerId, String playerName, String modListPayload, 
                                           String modListHash, String nonce) {
        // 1. Validate nonce
        if (nonce == null || nonce.isEmpty()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                StandardMessages.HANDSHAKE_MISSING_NONCE);
            callbacks.logWarning("Received mod list from %s with invalid/missing nonce. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 2. Check for replay attack
        if (usedNonces.contains(nonce)) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_REPLAY,
                StandardMessages.HANDSHAKE_REPLAY);
            callbacks.logWarning("Received mod list from %s with replay nonce. Kicking.", playerName);
            return new ValidationResult(false, message);
        }

        // 3. Validate mod list exists
        if (modListPayload == null || modListPayload.isBlank()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_EMPTY_MOD_LIST,
                StandardMessages.HANDSHAKE_EMPTY_MOD_LIST);
            callbacks.logWarning("Received empty mod list from %s. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 4. Validate hash exists
        if (modListHash == null || modListHash.isEmpty()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_HASH,
                StandardMessages.HANDSHAKE_MISSING_HASH);
            callbacks.logWarning("Received mod list from {} with invalid/missing hash. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 5. Verify hash matches payload
        String calculatedHash = HashUtils.sha256Hex(modListPayload);
        if (!calculatedHash.equals(modListHash)) {
            if (debugMode) {
                callbacks.logWarning("Received mod list from %s with mismatched hash. Expected %s but got %s", 
                    playerName, calculatedHash, modListHash);
            }
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_HASH_MISMATCH,
                StandardMessages.HANDSHAKE_HASH_MISMATCH);
            return new ValidationResult(false, message);
        }

        // 6. Mark nonce as used
        usedNonces.add(nonce);

        // 7. Parse mod list
        Set<String> mods = new HashSet<>();
        for (String s : modListPayload.split(",")) {
            if (!s.isBlank()) {
                mods.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }

        if (debugMode) {
            callbacks.logInfo("Received mod list from %s with %s mods, nonce: %s", 
                playerName, mods.size(), nonce);
        }

        // 8. Sync to database
        callbacks.syncPlayerMods(playerId, playerName, mods);

        // 9. Update client info
        ClientInfo oldInfo = clients.get(playerId);
        ClientInfo newInfo = new ClientInfo(
            oldInfo != null && oldInfo.fabric(),  // preserve fabric flag
            mods,
            oldInfo != null && oldInfo.signatureVerified(),
            oldInfo != null && oldInfo.veltonVerified(),
            nonce,
            oldInfo != null ? oldInfo.integrityNonce() : null,
            oldInfo != null ? oldInfo.veltonNonce() : null,
            oldInfo != null && oldInfo.checked()  // preserve checked flag
        );
        clients.put(playerId, newInfo);

        return new ValidationResult(true, null, newInfo);
    }

    /**
     * Validates an integrity payload and updates client info.
     * Checks: nonce validity, signature, jar hash, signature verification.
     */
    public ValidationResult validateIntegrity(UUID playerId, String playerName, byte[] clientSignature, 
                                             String jarHash, String nonce) {
        // 1. Validate nonce
        if (nonce == null || nonce.isEmpty()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                StandardMessages.HANDSHAKE_MISSING_NONCE);
            callbacks.logWarning("Received integrity payload from %s with invalid/missing nonce. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 2. Validate signature exists
        if (clientSignature == null || clientSignature.length == 0) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_SIGNATURE,
                StandardMessages.HANDSHAKE_MISSING_SIGNATURE);
            callbacks.logWarning("Received integrity payload from %s with invalid/missing signature. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 3. Check for legacy format (single byte)
        if (clientSignature.length == 1) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_OUTDATED_CLIENT,
                StandardMessages.DEFAULT_OUTDATED_CLIENT_MESSAGE);
            callbacks.logWarning("Received legacy integrity payload from %s. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 4. Validate jar hash exists
        if (jarHash == null || jarHash.isEmpty()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_JAR_HASH,
                StandardMessages.HANDSHAKE_MISSING_JAR_HASH);
            callbacks.logWarning("Received integrity payload from %s with invalid/missing jar hash. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 5. Verify signature
        boolean verified = verifySignature(jarHash, clientSignature, playerName);

        if (debugMode) {
            callbacks.logInfo("Integrity check for %s: %s", playerName, verified ? "PASSED" : "FAILED");
        } else {
            callbacks.logInfo("%s - Integrity: %s", playerName, verified ? "PASSED" : "FAILED");
        }

        // 6. Update client info
        ClientInfo oldInfo = clients.get(playerId);
        ClientInfo newInfo = new ClientInfo(
            oldInfo != null && oldInfo.fabric(),  // preserve fabric flag
            oldInfo != null ? oldInfo.mods() : Collections.emptySet(),
            verified,
            oldInfo != null && oldInfo.veltonVerified(),
            oldInfo != null ? oldInfo.modListNonce() : null,
            nonce,
            oldInfo != null ? oldInfo.veltonNonce() : null,
            oldInfo != null && oldInfo.checked()  // preserve checked flag
        );
        clients.put(playerId, newInfo);

        // 7. Trigger player check
        callbacks.checkPlayer(playerId, playerName, newInfo);

        return new ValidationResult(true, null, newInfo);
    }

    /**
     * Validates a Velton payload and updates client info.
     * Checks: nonce validity, signature hash existence.
     */
    public ValidationResult validateVelton(UUID playerId, String playerName, String signatureHash, String nonce) {
        // 1. Validate nonce
        if (nonce == null || nonce.isEmpty()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                StandardMessages.HANDSHAKE_MISSING_NONCE);
            callbacks.logWarning("Received Velton payload from %s with invalid/missing nonce. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 2. Check if signature hash exists (indicates verification)
        boolean verified = signatureHash != null && !signatureHash.isEmpty();

        if (debugMode) {
            callbacks.logInfo("Velton check for %s with nonce %s: %s", playerName, nonce, verified ? "PASSED" : "FAILED");
        } else {
            callbacks.logInfo("%s - Velton: %s", playerName, verified ? "PASSED" : "FAILED");
        }

        // 3. Fail if not verified
        if (!verified) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_VELTON_FAILED,
                StandardMessages.VELTON_VERIFICATION_FAILED);
            callbacks.logWarning("Kicking %s - Velton signature verification failed", playerName);
            return new ValidationResult(false, message);
        }

        // 4. Update client info
        ClientInfo oldInfo = clients.get(playerId);
        ClientInfo newInfo = new ClientInfo(
            oldInfo != null && oldInfo.fabric(),  // preserve fabric flag
            oldInfo != null ? oldInfo.mods() : Collections.emptySet(),
            oldInfo != null && oldInfo.signatureVerified(),
            verified,
            oldInfo != null ? oldInfo.modListNonce() : null,
            oldInfo != null ? oldInfo.integrityNonce() : null,
            nonce,
            oldInfo != null && oldInfo.checked()  // preserve checked flag
        );
        clients.put(playerId, newInfo);

        // 5. Trigger player check
        callbacks.checkPlayer(playerId, playerName, newInfo);

        return new ValidationResult(true, null, newInfo);
    }

    private boolean verifySignature(String jarHash, byte[] signatureBytes, String playerName) {
        if (signatureVerifier == null || !signatureVerifier.isKeyLoaded()) {
            callbacks.logWarning("Cannot verify signature for %s: public key not loaded", playerName);
            return false;
        }

        if (signatureBytes.length < 128) {
            callbacks.logWarning("Integrity check for %s: signature too small to be valid", playerName);
            return false;
        }

        try {
            boolean verified = signatureVerifier.verifySignature(jarHash, signatureBytes);
            if (verified) {
                if (debugMode) {
                    String shortHash = jarHash.length() > 8 ? jarHash.substring(0, 8) : jarHash;
                    callbacks.logInfo("Integrity check for %s: JAR SIGNED with VALID SIGNATURE (hash: %s)", 
                        playerName, shortHash);
                }
            } else {
                callbacks.logWarning("Integrity check for %s: signature verification FAILED - signature was not created with our key", 
                    playerName);
            }
            return verified;
        } catch (Exception e) {
            callbacks.logWarning("Integrity check for %s: error verifying signature: %s", playerName, e.getMessage());
            return false;
        }
    }

    public void clearNonceHistory() {
        usedNonces.clear();
    }

    /**
     * Result of payload validation
     */
    public static class ValidationResult {
        public final boolean success;
        public final String errorMessage;
        public final ClientInfo updatedInfo;

        public ValidationResult(boolean success, String errorMessage) {
            this(success, errorMessage, null);
        }

        public ValidationResult(boolean success, String errorMessage, ClientInfo updatedInfo) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.updatedInfo = updatedInfo;
        }
    }

    /**
     * Callback interface for platform-specific actions
     */
    public interface PayloadValidationCallbacks {
        String getMessageOrDefault(String key, String defaultMessage);
        void logInfo(String format, Object... args);
        void logWarning(String format, Object... args);
        void syncPlayerMods(UUID playerId, String playerName, Set<String> mods);
        void checkPlayer(UUID playerId, String playerName, ClientInfo info);
    }
}
