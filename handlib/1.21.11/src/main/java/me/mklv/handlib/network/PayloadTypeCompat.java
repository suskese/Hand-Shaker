package me.mklv.handlib.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

@SuppressWarnings("null")
public final class PayloadTypeCompat {
    private PayloadTypeCompat() {
    }

    public static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String namespace, String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(namespace, path));
    }
}