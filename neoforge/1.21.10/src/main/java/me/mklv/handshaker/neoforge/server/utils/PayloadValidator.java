package me.mklv.handshaker.neoforge.server.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

public class PayloadValidator {

    @SuppressWarnings("null")
    public static boolean validateNonce(String nonce, ServerPlayer player, Logger logger, String payloadType) {
        if (nonce == null || nonce.isEmpty()) {
            logger.warn("Received {} from {} with invalid/missing nonce. Rejecting.", payloadType, player.getName().getString());
            player.connection.disconnect(Component.literal("Invalid handshake: missing nonce"));
            return false;
        }
        return true;
    }
}
