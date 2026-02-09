package me.mklv.handshaker.fabric.server.utils;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;

public class PayloadValidator {

    public static boolean validateNonce(String nonce, ServerPlayerEntity player, Logger logger, String payloadType) {
        if (nonce == null || nonce.isEmpty()) {
            logger.warn("Received {} from {} with invalid/missing nonce. Rejecting.", payloadType, player.getName().getString());
            player.networkHandler.disconnect(Text.of("Invalid handshake: missing nonce"));
            return false;
        }
        return true;
    }
}
