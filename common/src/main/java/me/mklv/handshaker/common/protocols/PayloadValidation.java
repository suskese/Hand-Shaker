package me.mklv.handshaker.common.protocols;

import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;
import me.mklv.handshaker.common.protocols.LegacyVersion.ClientProfile;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.HashUtils;
import me.mklv.handshaker.common.utils.PayloadCompression;
import me.mklv.handshaker.common.utils.RateLimiter;
import me.mklv.handshaker.common.utils.SignatureVerifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Unified payload validation system for all platforms (Paper, Fabric, NeoForge).
 * Handles validation logic for mod lists, integrity checks, and Velton payloads.
 * Provides callback interface for platform-specific actions.
 */
public class PayloadValidation {
    private static final Pattern SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");
    private static final long NONCE_TTL_MILLIS = 10 * 60 * 1000L;
    private final PayloadValidationCallbacks callbacks;
    private final SignatureVerifier signatureVerifier;
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();
    private final Map<UUID, ClientInfo> clients;
    private final RateLimiter rateLimiter;
    private boolean debugMode = false;

    public PayloadValidation(PayloadValidationCallbacks callbacks, SignatureVerifier signatureVerifier, 
                            Map<UUID, ClientInfo> clients) {
        this.callbacks = callbacks;
        this.signatureVerifier = signatureVerifier;
        this.clients = clients;
        int perMinute = callbacks != null ? callbacks.getRateLimitPerMinute() : 10;
        this.rateLimiter = new RateLimiter(Math.max(1, perMinute), 60);
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
        if (callbacks.isRateLimitEnabled() && !rateLimiter.tryConsume(playerId)) {
            callbacks.logWarning("Rate limit exceeded for %s while sending mod list payload", playerName);
            return new ValidationResult(false, "Too many handshake payloads. Please retry shortly.");
        }

        String decodedPayload = modListPayload;
        if (callbacks.isPayloadCompressionEnabled()) {
            Optional<String> inflated = PayloadCompression.decodeEnvelope(modListPayload);
            if (inflated.isEmpty()) {
                callbacks.logWarning("Failed to decode compressed mod list payload from %s", playerName);
                return new ValidationResult(false, callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
            }
            decodedPayload = inflated.get();
        }

        // 1. Validate nonce
        if (nonce == null || nonce.isEmpty()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                StandardMessages.HANDSHAKE_MISSING_NONCE);
            callbacks.logWarning("Received mod list from %s with invalid/missing nonce. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 2. Check for replay attack
        if (!markNonceUsed(playerId, nonce)) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_REPLAY,
                StandardMessages.HANDSHAKE_REPLAY);
            callbacks.logWarning("Received mod list from %s with replay nonce. Kicking.", playerName);
            return new ValidationResult(false, message);
        }

        // 3. Validate mod list exists
        if (decodedPayload == null || decodedPayload.isBlank()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_EMPTY_MOD_LIST,
                StandardMessages.HANDSHAKE_EMPTY_MOD_LIST);
            callbacks.logWarning("Received empty mod list from %s. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 4. Validate hash exists
        boolean hashMissingOrInvalid = false;
        if (modListHash == null || modListHash.isEmpty()) {
            hashMissingOrInvalid = true;
        }

        // 5. Verify hash matches payload (modern/hybrid path)
        if (!hashMissingOrInvalid) {
            String calculatedHash = HashUtils.sha256Hex(decodedPayload);
            if (!calculatedHash.equals(modListHash)) {
                hashMissingOrInvalid = true;
                if (debugMode) {
                    callbacks.logWarning("Received mod list from %s with mismatched hash. Expected %s but got %s",
                        playerName, calculatedHash, modListHash);
                }
            }
        }

        ClientProfile profile = LegacyVersion.detectByPayload(decodedPayload, modListHash, hashMissingOrInvalid);
        if (debugMode) {
            callbacks.logInfo("Detected mod-list profile for %s: %s", playerName, profile.name().toLowerCase(Locale.ROOT));
        }
        if (!LegacyVersion.isAllowed(
            profile,
            callbacks.isModernCompatibilityEnabled(),
            callbacks.isHybridCompatibilityEnabled(),
            callbacks.isLegacyCompatibilityEnabled()
        )) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_OUTDATED_CLIENT,
                StandardMessages.DEFAULT_OUTDATED_CLIENT_MESSAGE);
            callbacks.logWarning("Received %s mod-list payload from %s but this compatibility mode is disabled.",
                profile.name().toLowerCase(Locale.ROOT), playerName);
            return new ValidationResult(false, message);
        }

        if (hashMissingOrInvalid && profile != ClientProfile.LEGACY) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_HASH_MISMATCH,
                StandardMessages.HANDSHAKE_HASH_MISMATCH);
            return new ValidationResult(false, message);
        }

        // 6. Parse mod list
        Set<String> mods = new HashSet<>();
        for (String s : decodedPayload.split(",")) {
            if (!s.isBlank()) {
                mods.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }

        if (debugMode) {
            callbacks.logInfo("Received mod list from %s with %s mods, nonce: %s", 
                playerName, mods.size(), nonce);
        }

        // 7. Sync to database
        callbacks.syncPlayerMods(playerId, playerName, mods);

        // 8. Update client info
        ClientInfo oldInfo = clients.get(playerId);
        ClientInfo newInfo = new ClientInfo(
            oldInfo != null && oldInfo.fabric(),  // preserve fabric flag
            mods,
            oldInfo != null && oldInfo.signatureVerified(),
            oldInfo != null && oldInfo.veltonVerified(),
            nonce,
            oldInfo != null ? oldInfo.integrityNonce() : null,
            oldInfo != null ? oldInfo.veltonNonce() : null,
            oldInfo != null && oldInfo.checked()  // preserve checked flag to prevent double action execution
        );
        clients.put(playerId, newInfo);

        // 9. Trigger player check with updated mod info
        callbacks.checkPlayer(playerId, playerName, newInfo);

        return new ValidationResult(true, null, newInfo);
    }

    /**
     * Validates an integrity payload and updates client info.
     * Checks: nonce validity, signature, jar hash, signature verification.
     */
    public ValidationResult validateIntegrity(UUID playerId, String playerName, byte[] clientSignature, 
                                             String jarHash, String nonce) {
        if (callbacks.isRateLimitEnabled() && !rateLimiter.tryConsume(playerId)) {
            callbacks.logWarning("Rate limit exceeded for %s while sending integrity payload", playerName);
            return new ValidationResult(false, "Too many handshake payloads. Please retry shortly.");
        }

        ClientInfo previousInfo = clients.get(playerId);
        ClientProfile profile = LegacyVersion.detectByMods(previousInfo != null ? previousInfo.mods() : Collections.emptySet());
        if (debugMode) {
            callbacks.logInfo("Detected integrity profile for %s: %s", playerName, profile.name().toLowerCase(Locale.ROOT));
        }

        if (profile == ClientProfile.HYBRID && !callbacks.isHybridCompatibilityEnabled()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_OUTDATED_CLIENT,
                StandardMessages.DEFAULT_OUTDATED_CLIENT_MESSAGE);
            callbacks.logWarning("Received hybrid(v6) integrity payload from %s while hybrid compatibility is disabled.", playerName);
            return new ValidationResult(false, message);
        }

        // 1. Validate nonce
        if (nonce == null || nonce.isEmpty()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                StandardMessages.HANDSHAKE_MISSING_NONCE);
            callbacks.logWarning("Received integrity payload from %s with invalid/missing nonce. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 2. Check for replay attack
        if (!markNonceUsed(playerId, nonce)) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_REPLAY,
                StandardMessages.HANDSHAKE_REPLAY);
            callbacks.logWarning("Received integrity payload from %s with replay nonce. Kicking.", playerName);
            return new ValidationResult(false, message);
        }

        // 3. Validate signature exists
        if (clientSignature == null || clientSignature.length == 0) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_INVALID_SIGNATURE,
                StandardMessages.DEFAULT_INVALID_SIGNATURE_MESSAGE);
            callbacks.logWarning("Received integrity payload from %s with invalid/missing signature. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 4. Check for legacy format (single byte)
        if (clientSignature.length == 1) {
            if (profile == ClientProfile.HYBRID && callbacks.isHybridCompatibilityEnabled()) {
                boolean verified = clientSignature[0] != 0;
                if (!verified) {
                    String message = callbacks.getMessageOrDefault(StandardMessages.KEY_INVALID_SIGNATURE,
                        StandardMessages.DEFAULT_INVALID_SIGNATURE_MESSAGE);
                    callbacks.logWarning("Hybrid(v6) integrity marker from %s indicates invalid signature.", playerName);
                    return new ValidationResult(false, message);
                }
                return updateIntegrityResult(playerId, playerName, nonce, true);
            }

            if (callbacks.isLegacyCompatibilityEnabled()) {
                return updateIntegrityResult(playerId, playerName, nonce, true);
            }

            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_OUTDATED_CLIENT,
                StandardMessages.DEFAULT_OUTDATED_CLIENT_MESSAGE);
            callbacks.logWarning("Received legacy integrity payload from %s. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        if (callbacks.isUnsignedCompatibilityEnabled()) {
            return updateIntegrityResult(playerId, playerName, nonce, true);
        }

        // 5. Validate jar hash exists
        if (jarHash == null || jarHash.isEmpty()) {
            if (callbacks.isLegacyCompatibilityEnabled()) {
                return updateIntegrityResult(playerId, playerName, nonce, true);
            }
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_JAR_HASH,
                StandardMessages.HANDSHAKE_MISSING_JAR_HASH);
            callbacks.logWarning("Received integrity payload from %s with invalid/missing jar hash. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 6. Validate jar hash format
        if (!SHA256_HEX.matcher(jarHash).matches()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_JAR_HASH,
                StandardMessages.HANDSHAKE_MISSING_JAR_HASH);
            callbacks.logWarning("Received integrity payload from %s with malformed jar hash. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        if (debugMode) {
            callbacks.logInfo("Integrity payload hash from %s: %s", playerName, jarHash);
        }

        if (profile == ClientProfile.HYBRID && callbacks.isHybridCompatibilityEnabled()) {
            if (!LegacyVersion.isTrustedHybridJarHash(jarHash)) {
                String message = callbacks.getMessageOrDefault(StandardMessages.KEY_INVALID_SIGNATURE,
                    StandardMessages.DEFAULT_INVALID_SIGNATURE_MESSAGE);
                callbacks.logWarning("Hybrid(v6) integrity payload from %s rejected: untrusted jar hash %s", playerName, jarHash);
                return new ValidationResult(false, message);
            }

            boolean verified = verifyHybridSignature(jarHash, clientSignature, playerName);
            return updateIntegrityResult(playerId, playerName, nonce, verified);
        }

        // 7. Verify signature
        boolean verified = verifySignature(jarHash, clientSignature, playerName);

        if (debugMode) {
            callbacks.logInfo("Integrity check for %s: %s", playerName, verified ? "PASSED" : "FAILED");
        } else {
            callbacks.logInfo("%s - Integrity: %s", playerName, verified ? "PASSED" : "FAILED");
        }

        // 8. Update client info
        return updateIntegrityResult(playerId, playerName, nonce, verified);
    }

    private boolean verifyHybridSignature(String jarHash, byte[] signatureBytes, String playerName) {
        if (signatureVerifier != null && signatureVerifier.containsExpectedSignerCertificate(signatureBytes)) {
            callbacks.logInfo("%s - Integrity: PASSED (hybrid certificate-chain validation)", playerName);
            return true;
        }

        if (verifySignature(jarHash, signatureBytes, playerName)) {
            return true;
        }

        callbacks.logWarning("Integrity check for %s: hybrid(v6) verification failed", playerName);
        return false;
    }

    private ValidationResult updateIntegrityResult(UUID playerId, String playerName, String nonce, boolean verified) {
        ClientInfo oldInfo = clients.get(playerId);
        ClientInfo newInfo = new ClientInfo(
            oldInfo != null && oldInfo.fabric(),  // preserve fabric flag
            oldInfo != null ? oldInfo.mods() : Collections.emptySet(),
            verified,
            oldInfo != null && oldInfo.veltonVerified(),
            oldInfo != null ? oldInfo.modListNonce() : null,
            nonce,
            oldInfo != null ? oldInfo.veltonNonce() : null,
            oldInfo != null && oldInfo.checked()  // preserve checked flag to prevent double action execution
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
        if (callbacks.isRateLimitEnabled() && !rateLimiter.tryConsume(playerId)) {
            callbacks.logWarning("Rate limit exceeded for %s while sending velton payload", playerName);
            return new ValidationResult(false, "Too many handshake payloads. Please retry shortly.");
        }

        // 1. Validate nonce
        if (nonce == null || nonce.isEmpty()) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_MISSING_NONCE,
                StandardMessages.HANDSHAKE_MISSING_NONCE);
            callbacks.logWarning("Received Velton payload from %s with invalid/missing nonce. Rejecting.", playerName);
            return new ValidationResult(false, message);
        }

        // 2. Check for replay attack
        if (!markNonceUsed(playerId, nonce)) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_REPLAY,
                StandardMessages.HANDSHAKE_REPLAY);
            callbacks.logWarning("Received Velton payload from %s with replay nonce. Kicking.", playerName);
            return new ValidationResult(false, message);
        }

        // 3. Check if signature hash exists (indicates verification)
        boolean verified = signatureHash != null && !signatureHash.isEmpty();

        if (debugMode) {
            callbacks.logInfo("Velton check for %s with nonce %s: %s", playerName, nonce, verified ? "PASSED" : "FAILED");
        } else {
            callbacks.logInfo("%s - Velton: %s", playerName, verified ? "PASSED" : "FAILED");
        }

        // 4. Fail if not verified
        if (!verified) {
            String message = callbacks.getMessageOrDefault(StandardMessages.KEY_VELTON_FAILED,
                StandardMessages.VELTON_VERIFICATION_FAILED);
            callbacks.logWarning("Kicking %s - Velton signature verification failed", playerName);
            return new ValidationResult(false, message);
        }

        // 5. Update client info
        ClientInfo oldInfo = clients.get(playerId);
        ClientInfo newInfo = new ClientInfo(
            oldInfo != null && oldInfo.fabric(),  // preserve fabric flag
            oldInfo != null ? oldInfo.mods() : Collections.emptySet(),
            oldInfo != null && oldInfo.signatureVerified(),
            verified,
            oldInfo != null ? oldInfo.modListNonce() : null,
            oldInfo != null ? oldInfo.integrityNonce() : null,
            nonce,
            oldInfo != null && oldInfo.checked()  // preserve checked flag to prevent double action execution
        );
        clients.put(playerId, newInfo);

        // 6. Trigger player check
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

    public void clearNonceHistory(UUID playerId) {
        if (playerId == null) {
            return;
        }
        String prefix = playerId + ":";
        usedNonces.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void cleanupExpiredNoncesNow() {
        cleanupExpiredNonces();
    }

    private boolean markNonceUsed(UUID playerId, String nonce) {
        cleanupExpiredNonces();
        String nonceKey = buildNonceKey(playerId, nonce);
        return usedNonces.putIfAbsent(nonceKey, System.currentTimeMillis()) == null;
    }

    private String buildNonceKey(UUID playerId, String nonce) {
        return playerId + ":" + nonce;
    }

    private void cleanupExpiredNonces() {
        long now = System.currentTimeMillis();
        usedNonces.entrySet().removeIf(entry -> now - entry.getValue() > NONCE_TTL_MILLIS);
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
        boolean isModernCompatibilityEnabled();
        boolean isHybridCompatibilityEnabled();
        boolean isLegacyCompatibilityEnabled();
        boolean isUnsignedCompatibilityEnabled();
        default boolean isRateLimitEnabled() { return true; }
        default int getRateLimitPerMinute() { return 10; }
        default boolean isPayloadCompressionEnabled() { return true; }
        void logInfo(String format, Object... args);
        void logWarning(String format, Object... args);
        void syncPlayerMods(UUID playerId, String playerName, Set<String> mods);
        void checkPlayer(UUID playerId, String playerName, ClientInfo info);
    }
}
