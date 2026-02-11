package me.mklv.handshaker.neoforge;

import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import me.mklv.handshaker.neoforge.server.HandShakerServerMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.mklv.handshaker.common.utils.HashUtils;

import java.security.MessageDigest;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.JarFile;

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
                .map(IModInfo::getModId)
                .map(id -> id.equals(MOD_ID) ? "hand-shaker" : id)
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        String modListHash = HashUtils.sha256Hex(payload);
        String nonce = generateNonce();
        sendPacket(event, new HandShakerServerMod.ModsListPayload(payload, modListHash, nonce));
        LOGGER.info("Sent mod list ({} chars, hash: {}) with nonce: {}", payload.length(), modListHash.substring(0, 8), nonce);
    }

    private void sendSignature(ClientPlayerNetworkEvent.LoggingIn event) {
        Optional<String> jarContentHash = computeJarContentHash();
        String nonce = generateNonce();
        Optional<Boolean> isSignatureValid = verifyJarSignatureLocally();
        
        // Send the jarHash. If signature verification failed locally, send empty bytes as indicator
        byte[] signatureIndicator = (isSignatureValid.isPresent() && isSignatureValid.get()) ? new byte[]{1} : new byte[0];
        
        if (jarContentHash.isPresent()) {
            sendPacket(event, new HandShakerServerMod.IntegrityPayload(signatureIndicator, jarContentHash.get(), nonce));
            String status = (isSignatureValid.isPresent() && isSignatureValid.get()) ? "VERIFIED" : "UNVERIFIED";
            LOGGER.info("Sent JAR content hash {} [{}] with nonce: {}", jarContentHash.get().substring(0, 8), status, nonce);
        } else {
            LOGGER.warn("Could not compute JAR content hash. Sending empty payload.");
            sendPacket(event, new HandShakerServerMod.IntegrityPayload(new byte[0], "", nonce));
        }
    }

    @SuppressWarnings("null")
    private void sendPacket(ClientPlayerNetworkEvent.LoggingIn event, CustomPacketPayload payload) {
        if (event.getPlayer() != null && event.getPlayer().connection != null) {
            event.getPlayer().connection.send(new ServerboundCustomPayloadPacket(payload));
        }
    }

    private Optional<String> computeJarContentHash() {
        try {
            var classPath = HandShakerClientMod.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            var path = java.nio.file.Paths.get(classPath);
            
            if (!java.nio.file.Files.isRegularFile(path)) {
                LOGGER.error("Not running from JAR file: {}", path);
                return Optional.empty();
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var zipFile = new java.util.zip.ZipFile(path.toFile())) {
                // Collect and sort entries for consistent hashing
                var entries = java.util.Collections.list(zipFile.entries()).stream()
                        .filter(e -> {
                            String name = e.getName().toUpperCase();
                            // Skip signature files
                            return !name.startsWith("META-INF/") || 
                                   (!name.endsWith(".SF") && !name.endsWith(".RSA") && 
                                    !name.endsWith(".DSA") && !name.equals("META-INF/MANIFEST.MF"));
                        })
                        .sorted(java.util.Comparator.comparing(java.util.zip.ZipEntry::getName))
                        .toList();

                if (entries.isEmpty()) {
                    LOGGER.error("JAR has no content files");
                    return Optional.empty();
                }

                for (var entry : entries) {
                    try (var is = zipFile.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) > 0) {
                            digest.update(buffer, 0, read);
                        }
                    }
                }

                String hexHash = HashUtils.toHex(digest.digest());
                LOGGER.info("Computed JAR content hash ({} files): {}", entries.size(), hexHash.substring(0, 8));
                return Optional.of(hexHash);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to compute JAR content hash: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Boolean> verifyJarSignatureLocally() {
        try {
            var classPath = HandShakerClientMod.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI();
            var path = java.nio.file.Paths.get(classPath);
            
            if (path == null || !java.nio.file.Files.isRegularFile(path)) {
                return Optional.empty();
            }

            try (var jarFile = new JarFile(path.toFile())) {
                var entries = java.util.Collections.list(jarFile.entries());
                for (var entry : entries) {
                    try (var is = jarFile.getInputStream(entry)) {
                        byte[] buffer = new byte[8192];
                        while (is.read(buffer) > 0) {
                            // Reading triggers verification
                        }
                    }
                }
                
                LOGGER.info("JAR signature verified successfully on client side");
                return Optional.of(true);
            } catch (java.io.IOException e) {
                LOGGER.error("JAR signature verification FAILED: {}", e.getMessage());
                return Optional.of(false);
            }
        } catch (Exception e) {
            LOGGER.error("Error verifying JAR signature: {}", e.getMessage());
            return Optional.of(false);
        }
    }

    private String generateNonce() {
        return UUID.randomUUID().toString();
    }

}
