package me.mklv.handshaker.neoforge;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import me.mklv.handshaker.neoforge.server.HandShakerServerMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.mklv.handshaker.common.utils.HashUtils;
import me.mklv.handshaker.common.utils.JarIntegrityProof;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

@Mod(HandShakerClientMod.MOD_ID)
public class HandShakerClientMod {
    public static final String MOD_ID = "hand_shaker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public HandShakerClientMod(IEventBus modEventBus) {
        LOGGER.info("HandShaker client initializing");
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        sendModList(event);
        sendSignature(event);
    }

    private void sendModList(ClientPlayerNetworkEvent.LoggingIn event) {
        String payload = ModList.get().getMods().stream()
                .map(mod -> {
                    String id = mod.getModId();
                    String normalizedId = id.equals(MOD_ID) ? "hand-shaker" : id;
                    String displayName = mod.getDisplayName();
                    String encodedDisplayName = encodeDisplayName(displayName);
                    String version = mod.getVersion().toString();
                String hash = resolveModFilePath(mod)
                    .flatMap(this::computeFileSha256)
                    .orElse("null");
                return normalizedId + "~" + encodedDisplayName + ":" + version + ":" + hash;
                })
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        String modListHash = HashUtils.sha256Hex(payload);
        String nonce = generateNonce();
        sendPacket(event, new HandShakerServerMod.ModsListPayload(payload, modListHash, nonce));
        LOGGER.info("Sent mod list ({} chars, hash: {}) with nonce: {}", payload.length(), modListHash.substring(0, 8), nonce);
    }

    private String encodeDisplayName(String name) {
        if (name == null || name.isBlank()) {
            return "null";
        }
        return URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private void sendSignature(ClientPlayerNetworkEvent.LoggingIn event) {
        Optional<JarIntegrityProof.Proof> proof = JarIntegrityProof.buildFromRuntimeJar(HandShakerClientMod.class, new JarIntegrityProof.LogSink() {
            @Override
            public void info(String message) {
                LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                LOGGER.warn(message);
            }
        });
        String nonce = generateNonce();

        if (proof.isPresent()) {
            JarIntegrityProof.Proof integrityProof = proof.get();
            sendPacket(event, new HandShakerServerMod.IntegrityPayload(integrityProof.signature(), integrityProof.jarHash(), nonce));
            LOGGER.info("Sent detached integrity proof ({} bytes) with content hash {} and nonce: {}",
                integrityProof.signature().length,
                integrityProof.jarHash().substring(0, 8),
                nonce);
        } else {
            LOGGER.warn("Could not build runtime integrity proof. Sending empty payload.");
            sendPacket(event, new HandShakerServerMod.IntegrityPayload(new byte[0], "", nonce));
        }
    }

    private void sendPacket(ClientPlayerNetworkEvent.LoggingIn event, CustomPacketPayload payload) {
        if (event.getPlayer() != null && event.getPlayer().connection != null) {
            event.getPlayer().connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }

    private String generateNonce() {
        return UUID.randomUUID().toString();
    }

    private Optional<Path> resolveModFilePath(Object modInfo) {
        try {
            Object owningFile = modInfo.getClass().getMethod("getOwningFile").invoke(modInfo);
            if (owningFile == null) {
                return Optional.empty();
            }
            Object modFile = owningFile.getClass().getMethod("getFile").invoke(owningFile);
            if (modFile == null) {
                return Optional.empty();
            }
            Object filePath = modFile.getClass().getMethod("getFilePath").invoke(modFile);
            if (filePath instanceof Path path) {
                return Optional.of(path);
            }
            if (filePath != null) {
                return Optional.of(Paths.get(filePath.toString()));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private Optional<String> computeFileSha256(Path path) {
        return HashUtils.sha256FileHex(path);
    }

}
