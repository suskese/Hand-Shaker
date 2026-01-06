package me.mklv.handshaker.server;

import me.mklv.handshaker.HandShaker;
import me.mklv.handshaker.server.BlacklistConfig.IntegrityMode;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("all")
public class HandShakerServer implements DedicatedServerModInitializer {
    public static final String MOD_ID = "hand-shaker";
    public static final Identifier VELTON_CHANNEL = Identifier.of("velton", "signature");
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID + "-server");
    private static HandShakerServer instance;
    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();
    private BlacklistConfig blacklistConfig;
    private PlayerHistoryDatabase playerHistoryDb;
    private MinecraftServer server;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private byte[] serverCertificate;

    public record ClientInfo(Set<String> mods, boolean signatureVerified, boolean veltonVerified, String modListNonce, String integrityNonce, String veltonNonce) {}

    public static HandShakerServer getInstance() {
        return instance;
    }

    @Override
    public void onInitializeServer() {
        instance = this;
        LOGGER.info("HandShaker server initializing");
        blacklistConfig = new BlacklistConfig();
        blacklistConfig.load();
        
        playerHistoryDb = new PlayerHistoryDatabase();

        loadServerCertificate();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> this.server = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            scheduler.shutdown();
            if (playerHistoryDb != null) {
                playerHistoryDb.close();
            }
        });

        // Register payload types
        PayloadTypeRegistry.playC2S().register(HandShaker.ModsListPayload.ID, HandShaker.ModsListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HandShaker.IntegrityPayload.ID, HandShaker.IntegrityPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VeltonPayload.ID, VeltonPayload.CODEC);

        // Register payload handlers
        ServerPlayNetworking.registerGlobalReceiver(HandShaker.ModsListPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            try {
                if (payload.nonce() == null || payload.nonce().isEmpty()) {
                    LOGGER.warn("Received mod list from {} with invalid/missing nonce. Rejecting.", player.getName().getString());
                    player.networkHandler.disconnect(Text.of("Invalid handshake: missing nonce"));
                    return;
                }
                Set<String> mods = new HashSet<>(Arrays.asList(payload.mods().split(",")));
                if (payload.mods().isEmpty()) {
                    mods.clear();
                }
                LOGGER.info("Received mod list from {} with nonce: {}", player.getName().getString(), payload.nonce());
                
                // Sync with database
                if (playerHistoryDb != null) {
                    playerHistoryDb.syncPlayerMods(player.getUuid(), player.getName().getString(), mods);
                }
                
                clients.compute(player.getUuid(), (uuid, oldInfo) ->
                        new ClientInfo(mods, 
                                oldInfo != null && oldInfo.signatureVerified(),
                                oldInfo != null && oldInfo.veltonVerified(),
                                payload.nonce(),
                                oldInfo != null ? oldInfo.integrityNonce() : null,
                                oldInfo != null ? oldInfo.veltonNonce() : null));
            } catch (Exception e) {
                LOGGER.error("Failed to decode mod list from {}. Terminating connection.", player.getName().getString(), e);
                player.networkHandler.disconnect(Text.of("Corrupted handshake data"));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(HandShaker.IntegrityPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            try {
                if (payload.nonce() == null || payload.nonce().isEmpty()) {
                    LOGGER.warn("Received integrity payload from {} with invalid/missing nonce. Rejecting.", player.getName().getString());
                    player.networkHandler.disconnect(Text.of("Invalid handshake: missing nonce"));
                    return;
                }
                byte[] clientCertificate = payload.signature();
                boolean verified = false;
                if (clientCertificate != null && clientCertificate.length > 0 && this.serverCertificate.length > 0) {
                    verified = Arrays.equals(clientCertificate, this.serverCertificate);
                }
                LOGGER.info("Integrity check for {} with nonce {}: {}", player.getName().getString(), payload.nonce(), verified ? "PASSED" : "FAILED");

                final boolean finalVerified = verified;
                clients.compute(player.getUuid(), (uuid, oldInfo) ->
                        new ClientInfo(oldInfo != null ? oldInfo.mods() : Collections.emptySet(), 
                                finalVerified,
                                oldInfo != null && oldInfo.veltonVerified(),
                                oldInfo != null ? oldInfo.modListNonce() : null,
                                payload.nonce(),
                                oldInfo != null ? oldInfo.veltonNonce() : null));
                blacklistConfig.checkPlayer(player, clients.get(player.getUuid()));
            } catch (Exception e) {
                LOGGER.error("Failed to decode integrity payload from {}. Terminating connection.", player.getName().getString(), e);
                player.networkHandler.disconnect(Text.of("Corrupted handshake data"));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(VeltonPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            try {
                if (payload.nonce() == null || payload.nonce().isEmpty()) {
                    LOGGER.warn("Received Velton payload from {} with invalid/missing nonce. Rejecting.", player.getName().getString());
                    player.networkHandler.disconnect(Text.of("Invalid handshake: missing nonce"));
                    return;
                }
                String signatureHash = payload.signatureHash();
                boolean verified = signatureHash != null && !signatureHash.isEmpty();
                
                LOGGER.info("Velton check for {} with nonce {}: {}", player.getName().getString(), payload.nonce(), verified ? "PASSED" : "FAILED");

                // Kick player if Velton signature is invalid/missing
                if (!verified) {
                    LOGGER.warn("Kicking {} - Velton signature verification failed", player.getName().getString());
                    player.networkHandler.disconnect(Text.of("Anti-cheat verification failed"));
                    return;
                }

                final boolean finalVerified = verified;
                clients.compute(player.getUuid(), (uuid, oldInfo) ->
                        new ClientInfo(oldInfo != null ? oldInfo.mods() : Collections.emptySet(), 
                                oldInfo != null && oldInfo.signatureVerified(),
                                finalVerified,
                                oldInfo != null ? oldInfo.modListNonce() : null,
                                oldInfo != null ? oldInfo.integrityNonce() : null,
                                payload.nonce()));
                blacklistConfig.checkPlayer(player, clients.get(player.getUuid()));
            } catch (Exception e) {
                LOGGER.error("Failed to decode Velton payload from {}. Terminating connection.", player.getName().getString(), e);
                player.networkHandler.disconnect(Text.of("Corrupted handshake data"));
            }
        });

        // Register player lifecycle events
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            scheduler.schedule(() -> {
                server.execute(() -> {
                    if (handler.player.networkHandler == null) return; // Player disconnected
                    ClientInfo info = clients.computeIfAbsent(handler.player.getUuid(), uuid -> new ClientInfo(Collections.emptySet(), false, false, null, null, null));

                    blacklistConfig.checkPlayer(handler.player, info);
                });
            }, 5, TimeUnit.SECONDS);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clients.remove(handler.player.getUuid());
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            HandShakerCommand.register(dispatcher);
        });
    }

    private void loadServerCertificate() {
        try (InputStream is = HandShakerServer.class.getClassLoader().getResourceAsStream("public.cer")) {
            if (is == null) {
                if (blacklistConfig.getIntegrityMode() == IntegrityMode.SIGNED) {
                    LOGGER.error("Could not find 'public.cer' in the mod JAR. Integrity checking will fail.");
                }
                this.serverCertificate = new byte[0];
                return;
            }
            this.serverCertificate = is.readAllBytes();
            LOGGER.info("Successfully loaded embedded server certificate ({} bytes)", this.serverCertificate.length);
        } catch (IOException e) {
            LOGGER.error("Failed to load embedded server certificate", e);
            this.serverCertificate = new byte[0];
        }
    }

    public BlacklistConfig getBlacklistConfig() {
        return blacklistConfig;
    }

    public PlayerHistoryDatabase getPlayerHistoryDb() {
        return playerHistoryDb;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Map<UUID, ClientInfo> getClients() {
        return clients;
    }

    public void checkAllPlayers() {
        if (server == null) return;
        LOGGER.info("Re-checking all online players...");
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            blacklistConfig.checkPlayer(player, clients.getOrDefault(player.getUuid(), new ClientInfo(Collections.emptySet(), false, false, null, null, null)));
        }
    }

    public record VeltonPayload(String signatureHash, String nonce) implements CustomPayload {
        public static final CustomPayload.Id<VeltonPayload> ID = new CustomPayload.Id<>(VELTON_CHANNEL);
        public static final PacketCodec<PacketByteBuf, VeltonPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.STRING, VeltonPayload::signatureHash,
                PacketCodecs.STRING, VeltonPayload::nonce,
                VeltonPayload::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }
}