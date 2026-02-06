package me.mklv.handshaker.neoforge;

import me.mklv.handshaker.neoforge.server.HandShakerServerMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkSetup {
    private static final Logger LOGGER = LoggerFactory.getLogger("hand-shaker-network");

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1").optional();

        // Register payloads for client->server communication
        registrar.playToServer(
                HandShakerServerMod.ModsListPayload.TYPE,
                HandShakerServerMod.ModsListPayload.CODEC,
                (payload, context) -> {
                    // Handler delegates to server mod if available
                    HandShakerServerMod server = HandShakerServerMod.getInstance();
                    if (server != null) {
                        server.handleModsList(payload, context);
                    }
                }
        );

        registrar.playToServer(
                HandShakerServerMod.IntegrityPayload.TYPE,
                HandShakerServerMod.IntegrityPayload.CODEC,
                (payload, context) -> {
                    // Handler delegates to server mod if available
                    HandShakerServerMod server = HandShakerServerMod.getInstance();
                    if (server != null) {
                        server.handleIntegrity(payload, context);
                    }
                }
        );

        // Velton is optional - register without requiring it on the client
        final PayloadRegistrar veltonRegistrar = event.registrar("velton").optional();
        veltonRegistrar.playToServer(
                HandShakerServerMod.VeltonPayload.TYPE,
                HandShakerServerMod.VeltonPayload.CODEC,
                (payload, context) -> {
                    // Handler delegates to server mod if available
                    HandShakerServerMod server = HandShakerServerMod.getInstance();
                    if (server != null) {
                        server.handleVelton(payload, context);
                    }
                }
        );

        LOGGER.info("All payloads registered once at mod startup");
    }
}
