package me.mklv.handshaker.fabric.server.utils;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;

/**
 * Utility methods for validating network payloads and player actions.
 */
public class PayloadValidator {
    /**
     * Validates a nonce from a payload. Disconnects the player if validation fails.
     * @param nonce The nonce to validate
     * @param player The player to disconnect if validation fails
     * @param logger The logger to use for warnings
     * @param payloadType The name of the payload type (for logging)
     * @return True if nonce is valid, false otherwise (player is disconnected on failure)
     */
    public static boolean validateNonce(String nonce, ServerPlayerEntity player, Logger logger, String payloadType) {
        if (nonce == null || nonce.isEmpty()) {
            logger.warn("Received {} from {} with invalid/missing nonce. Rejecting.", payloadType, player.getName().getString());
            player.networkHandler.disconnect(Text.of("Invalid handshake: missing nonce"));
            return false;
        }
        return true;
    }
}
