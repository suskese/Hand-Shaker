package me.mklv.handshaker.paper.protocol;

import me.mklv.handshaker.paper.HandShakerPlugin;
import me.mklv.handshaker.common.configs.ActionDefinition;
import me.mklv.handshaker.common.utils.PlayerModStatus;
import me.mklv.handshaker.paper.configs.ConfigManager;
import me.mklv.handshaker.paper.utils.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.logging.Logger;

public class PluginProtocolHandler {
    private final HandShakerPlugin plugin;
    private final Map<UUID, ClientInfo> clients;
    private final Logger logger;
    private final SignatureVerifier signatureVerifier;
    private final PayloadDecoder payloadDecoder;
    private final ConfigManager configManager;

    public PluginProtocolHandler(HandShakerPlugin plugin, Map<UUID, ClientInfo> clients) {
        this.plugin = plugin;
        this.clients = clients;
        this.logger = plugin.getLogger();
        this.configManager = plugin.getConfigManager();
        this.payloadDecoder = new PayloadDecoder(logger);
        
        // Load public key for signature verification
        PublicKey publicKey = loadPublicCertificate();
        this.signatureVerifier = new SignatureVerifier(publicKey, logger);
    }

    public void registerPluginChannels() {
        // Register incoming channels
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, HandShakerPlugin.MODS_CHANNEL,
                (channel, player, message) -> handleModList(player, message));
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, HandShakerPlugin.INTEGRITY_CHANNEL,
                (channel, player, message) -> handleIntegrityPayload(player, message));
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, HandShakerPlugin.VELTON_CHANNEL,
                (channel, player, message) -> handleVeltonPayloadInternal(player, message));

        // Register outgoing channels
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, HandShakerPlugin.MODS_CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, HandShakerPlugin.INTEGRITY_CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, HandShakerPlugin.VELTON_CHANNEL);
    }

    public void unregisterPluginChannels() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, HandShakerPlugin.MODS_CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, HandShakerPlugin.INTEGRITY_CHANNEL);
        Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, HandShakerPlugin.VELTON_CHANNEL);
    }


    private PublicKey loadPublicCertificate() {
        try (var certStream = plugin.getClass().getResourceAsStream("/public.cer")) {
            if (certStream == null) {
                logger.warning("⚠️  public.cer not found in resources. Signature verification will be disabled.");
                logger.warning("⚠️  Mods signed with ANY certificate will be accepted.");
                return null;
            }

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(certStream);
            PublicKey publicKey = cert.getPublicKey();
            logger.info("✓ Loaded public certificate for signature verification");
            return publicKey;
        } catch (Exception e) {
            logger.warning("Failed to load public.cer: " + e.getMessage());
            logger.warning("⚠️  Signature verification will be disabled.");
            return null;
        }
    }

    private void handleModList(Player player, byte[] data) {
        try {
            String payload = payloadDecoder.decodeString(data);
            if (payload == null) {
                logger.warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                kickPlayer(player, "Corrupted handshake data");
                return;
            }

            // Decode hash
            PayloadDecoder.DecodeResult hashResult = payloadDecoder.decodeStringWithOffset(data, calculateOffset(data, payload.length()));
            if (hashResult == null || hashResult.value == null || ((String) hashResult.value).isEmpty()) {
                logger.warning("Received mod list from " + player.getName() + " with invalid/missing hash. Rejecting.");
                kickPlayer(player, "Invalid handshake: missing hash");
                return;
            }
            String modListHash = (String) hashResult.value;

            // Decode nonce
            PayloadDecoder.DecodeResult nonceResult = payloadDecoder.decodeStringWithOffset(data, hashResult.offset);
            if (nonceResult == null || nonceResult.value == null || ((String) nonceResult.value).isEmpty()) {
                logger.warning("Received mod list from " + player.getName() + " with invalid/missing nonce. Rejecting.");
                kickPlayer(player, "Invalid handshake: missing nonce");
                return;
            }
            String nonce = (String) nonceResult.value;

            validateAndSyncModList(player, payload, modListHash, nonce);
        } catch (Exception e) {
            logger.severe("Failed to decode mod list from " + player.getName() + ". Terminating connection: " + e.getMessage());
            kickPlayer(player, "Corrupted handshake data");
        }
    }

    public boolean validateAndSyncModList(Player player, String payload, String modListHash, String nonce) {
        // Verify hash matches payload
        String calculatedHash = hashString(payload);
        if (!calculatedHash.equals(modListHash)) {
            if (HandShakerPlugin.DEBUG) {
                logger.warning("Received mod list from " + player.getName() + " with mismatched hash. Expected " + calculatedHash + " but got " + modListHash);
            }
            kickPlayer(player, "Invalid handshake: hash mismatch");
            return false;
        }

        // Parse mod list
        Set<String> mods = new HashSet<>();
        if (!payload.isBlank()) {
            for (String s : payload.split(",")) {
                if (!s.isBlank()) mods.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }

        if (HandShakerPlugin.DEBUG) {
            logger.info("Received mod list from " + player.getName() + " with nonce: " + nonce);
        }

        // Sync with database asynchronously (Folia-compatible)
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db != null) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                try {
                    db.syncPlayerMods(player.getUniqueId(), player.getName(), mods);
                } catch (Exception dbEx) {
                    logger.warning("Failed to sync player mods to database: " + dbEx.getMessage());
                }
            });
        }

        // Update client info
        ClientInfo oldInfo = clients.get(player.getUniqueId());
        if (oldInfo == null) {
            clients.put(player.getUniqueId(), new ClientInfo(true, mods, false, false, nonce, null, null, false));
        } else {
            clients.put(player.getUniqueId(), new ClientInfo(true, mods, oldInfo.signatureVerified(), 
                    oldInfo.veltonVerified(), nonce, oldInfo.integrityNonce(), oldInfo.veltonNonce(), false));
        }
        return true;
    }

    private void handleIntegrityPayload(Player player, byte[] data) {
        try {
            // Decode signature (byte array)
            PayloadDecoder.DecodeResult sigResult = payloadDecoder.decodeByteArrayWithOffset(data, 0);
            if (sigResult == null) {
                logger.warning("Failed to decode integrity payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, "Corrupted handshake data");
                return;
            }
            byte[] clientSignature = (byte[]) sigResult.value;

            // Decode jar hash (string)
            PayloadDecoder.DecodeResult hashResult = payloadDecoder.decodeStringWithOffset(data, sigResult.offset);
            if (hashResult == null || hashResult.value == null || ((String) hashResult.value).isEmpty()) {
                logger.warning("Received integrity payload from " + player.getName() + " with invalid/missing jar hash. Rejecting.");
                kickPlayer(player, "Invalid handshake: missing jar hash");
                return;
            }
            String jarHash = (String) hashResult.value;

            // Decode nonce (string)
            PayloadDecoder.DecodeResult nonceResult = payloadDecoder.decodeStringWithOffset(data, hashResult.offset);
            if (nonceResult == null || nonceResult.value == null || ((String) nonceResult.value).isEmpty()) {
                logger.warning("Received integrity payload from " + player.getName() + " with invalid/missing nonce. Rejecting.");
                kickPlayer(player, "Invalid handshake: missing nonce");
                return;
            }
            String nonce = (String) nonceResult.value;

            handleIntegrityCheck(player, clientSignature, jarHash, nonce);
        } catch (Exception e) {
            logger.severe("Failed to decode integrity payload from " + player.getName() + ". Terminating connection: " + e.getMessage());
            kickPlayer(player, "Corrupted handshake data");
        }
    }

    public void handleIntegrityCheck(Player player, byte[] clientSignature, String jarHash, String nonce) {
        boolean verified = false;

        if (!jarHash.isEmpty() && clientSignature.length > 0) {
            if (!signatureVerifier.isKeyLoaded()) {
                logger.warning("Cannot verify signature for " + player.getName() + ": public key not loaded");
            } else if (clientSignature.length >= 128) {
                verified = signatureVerifier.verifySignature(jarHash, clientSignature);
                if (verified) {
                    logger.info("Integrity check for " + player.getName() + ": JAR SIGNED with VALID SIGNATURE (hash: " + jarHash.substring(0, 8) + ")");
                } else {
                    logger.warning("Integrity check for " + player.getName() + ": signature verification FAILED");
                }
            } else {
                logger.warning("Integrity check for " + player.getName() + ": signature too small to be valid");
            }
        } else if (clientSignature.length == 0) {
            logger.warning("Integrity check for " + player.getName() + ": no signature data received");
        } else if (jarHash.isEmpty()) {
            logger.warning("Integrity check for " + player.getName() + ": no JAR hash received");
        }

        if (HandShakerPlugin.DEBUG) {
            logger.info("Integrity check for " + player.getName() + " with nonce " + nonce + ": " + (verified ? "PASSED" : "FAILED"));
        }

        // Update client info
        ClientInfo oldInfo = clients.get(player.getUniqueId());
        boolean finalVerified = verified;
        if (oldInfo == null) {
            clients.put(player.getUniqueId(), new ClientInfo(false, Collections.emptySet(), finalVerified, false, null, nonce, null, false));
        } else {
            clients.put(player.getUniqueId(), new ClientInfo(oldInfo.fabric(), oldInfo.mods(), finalVerified,
                    oldInfo.veltonVerified(), oldInfo.modListNonce(), nonce, oldInfo.veltonNonce(), oldInfo.checked()));
        }
        checkPlayer(player, clients);
    }

    private void handleVeltonPayloadInternal(Player player, byte[] data) {
        try {
            // Decode signature (byte array)
            PayloadDecoder.DecodeResult sigResult = payloadDecoder.decodeByteArrayWithOffset(data, 0);
            if (sigResult == null) {
                logger.warning("Failed to decode Velton payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, "Corrupted handshake data");
                return;
            }
            byte[] clientSignature = (byte[]) sigResult.value;

            // Decode jar hash (string)
            PayloadDecoder.DecodeResult hashResult = payloadDecoder.decodeStringWithOffset(data, sigResult.offset);
            if (hashResult == null || hashResult.value == null || ((String) hashResult.value).isEmpty()) {
                logger.warning("Received Velton payload from " + player.getName() + " with invalid/missing jar hash. Rejecting.");
                kickPlayer(player, "Invalid handshake: missing jar hash");
                return;
            }
            String jarHash = (String) hashResult.value;

            // Decode nonce (string)
            PayloadDecoder.DecodeResult nonceResult = payloadDecoder.decodeStringWithOffset(data, hashResult.offset);
            if (nonceResult == null || nonceResult.value == null || ((String) nonceResult.value).isEmpty()) {
                logger.warning("Received Velton payload from " + player.getName() + " with invalid/missing nonce. Rejecting.");
                kickPlayer(player, "Invalid handshake: missing nonce");
                return;
            }
            String nonce = (String) nonceResult.value;

            handleVeltonPayload(player, clientSignature, jarHash, nonce);
        } catch (Exception e) {
            logger.severe("Failed to decode Velton payload from " + player.getName() + ". Terminating connection: " + e.getMessage());
            kickPlayer(player, "Corrupted handshake data");
        }
    }

    public void handleVeltonPayload(Player player, byte[] clientSignature, String jarHash, String nonce) {
        boolean verified = false;

        if (!jarHash.isEmpty() && clientSignature.length > 0) {
            if (!signatureVerifier.isKeyLoaded()) {
                logger.warning("Cannot verify Velton signature for " + player.getName() + ": public key not loaded");
            } else if (clientSignature.length >= 128) {
                verified = signatureVerifier.verifySignature(jarHash, clientSignature);
                if (verified) {
                    logger.info("Velton integrity check for " + player.getName() + ": JAR SIGNED with VALID SIGNATURE (hash: " + jarHash.substring(0, 8) + ")");
                } else {
                    logger.warning("Velton integrity check for " + player.getName() + ": signature verification FAILED");
                }
            } else {
                logger.warning("Velton integrity check for " + player.getName() + ": signature too small to be valid");
            }
        } else if (clientSignature.length == 0) {
            logger.warning("Velton integrity check for " + player.getName() + ": no signature data received");
        } else if (jarHash.isEmpty()) {
            logger.warning("Velton integrity check for " + player.getName() + ": no JAR hash received");
        }

        if (HandShakerPlugin.DEBUG) {
            logger.info("Velton check for " + player.getName() + " with nonce " + nonce + ": " + (verified ? "PASSED" : "FAILED"));
        }

        if (!verified) {
            logger.warning("Kicking " + player.getName() + " - Velton signature verification failed");
            kickPlayer(player, "Anti-cheat verification failed");
            return;
        }

        // Update client info
        ClientInfo oldInfo = clients.get(player.getUniqueId());
        if (oldInfo == null) {
            clients.put(player.getUniqueId(), new ClientInfo(false, Collections.emptySet(), false, verified, null, null, nonce, false));
        } else {
            clients.put(player.getUniqueId(), new ClientInfo(oldInfo.fabric(), oldInfo.mods(), oldInfo.signatureVerified(),
                    verified, oldInfo.modListNonce(), oldInfo.integrityNonce(), nonce, oldInfo.checked()));
        }
        checkPlayer(player, clients);
    }

    public void checkPlayer(Player player, Map<UUID, ClientInfo> clients) {
        // Check if Bedrock players are allowed
        if (configManager.isAllowBedrockPlayers() && isBedrockPlayer(player)) {
            logger.info("Bedrock player " + player.getName() + " allowed to join without mod checks");
            return;
        }

        ClientInfo info = clients.get(player.getUniqueId());
        if (info == null) return; // Player data not yet received

        // Skip if already checked
        if (info.checked()) {
            return;
        }

        // Handshake presence check
        if (configManager.getBehavior() == ConfigManager.Behavior.STRICT && !info.fabric()) {
            kickPlayer(player, configManager.getNoHandshakeKickMessage());
            return;
        }

        // Integrity Check (only if client has the mod or behavior is STRICT)
        if (info.fabric() && configManager.getIntegrityMode() == ConfigManager.IntegrityMode.SIGNED) {
            if (!info.signatureVerified()) {
                kickPlayer(player, configManager.getInvalidSignatureKickMessage());
                return;
            }
        }

        Set<String> mods = info.mods();

        // Check player and execute action if needed
        PlayerModStatus status = configManager.checkPlayerWithAction(player, mods);
        
        if (HandShakerPlugin.DEBUG) {
            logger.info("[DEBUG] checkPlayerWithAction returned: " + (status != null ? "status(" + status.getActionName() + ")" : "null"));
        }
        
        if (status != null && status.hasViolation()) {
            if (HandShakerPlugin.DEBUG) {
                logger.info("[DEBUG] Player " + player.getName() + " has violation, kicking");
            }
            kickPlayer(player, status.getKickMessage());
            return;
        }

        // Execute the action if player wasn't kicked
        if (status != null && player.isOnline()) {
            if (HandShakerPlugin.DEBUG) {
                logger.info("[DEBUG] Executing action for " + player.getName() + ": " + status.getActionName());
            }
            executeAction(player, status.getActionName(), status.getDetectedMods());
        }

        // Mark as checked to prevent double execution
        clients.put(player.getUniqueId(), info.withChecked(true));
    }

    private void executeAction(Player player, String actionName, Set<String> mods) {
        if (actionName == null || actionName.equals("none")) {
            return; // No action to execute
        }

        if (actionName.equalsIgnoreCase("log")) {
            String logMessage = configManager.replacePlaceholders("Mod check violation: {player} using {mod}", player, mods);
            logger.info(logMessage);
            return;
        }

        ActionDefinition action = configManager.getAction(actionName);
        if (action == null) {
            logger.warning("Action '" + actionName + "' not found in mods-actions.yml");
            return;
        }

        if (HandShakerPlugin.DEBUG) {
            logger.info("[DEBUG] Executing action '" + actionName + "' for player " + player.getName());
        }

        // Execute commands on main thread (Bukkit commands require main thread)
        // Note: We use getGlobalRegionScheduler().run() instead of async because
        // Bukkit.dispatchCommand() must execute on the main thread
        try {
            plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
                for (String command : action.getCommands()) {
                    String processedCommand = configManager.replacePlaceholders(command, player, mods);
                    try {
                        if (HandShakerPlugin.DEBUG) {
                            logger.info("[DEBUG] Executing command: " + processedCommand);
                        }
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
                        logger.fine("Executed action '" + actionName + "' command: " + processedCommand);
                    } catch (Exception e) {
                        logger.warning("Failed to execute action '" + actionName + "' command: " + processedCommand + " - " + e.getMessage());
                    }
                }

                if (action.shouldLog()) {
                    String logMessage = configManager.replacePlaceholders("Action '" + actionName + "' executed for {player} using {mod}", player, mods);
                    logger.info(logMessage);
                }
            });
        } catch (Exception e) {
            logger.warning("Failed to schedule command execution for action '" + actionName + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Kicks a player with a message
     */
    private void kickPlayer(Player player, String message) {
        player.kick(Component.text(message).color(NamedTextColor.RED));
    }

    private int calculateOffset(byte[] data, int stringLength) {
        // Skip the varint prefix
        int offset = 0;
        int numRead = 0;
        byte read;
        do {
            if (offset >= data.length) return offset;
            read = data[offset++];
            numRead++;
            if (numRead > 5) return offset;
        } while ((read & 0b10000000) != 0);
        
        return offset + stringLength;
    }

    /**
     * Hashes a string using SHA-256
     */
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.warning("SHA-256 not available: " + e.getMessage());
            return "";
        }
    }

    /**
     * Checks if a player is connecting via Bedrock
     */
    private boolean isBedrockPlayer(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Try Floodgate API first
        try {
            Class<?> floodgateApiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = floodgateApiClass.getMethod("getInstance").invoke(null);
            boolean isFloodgate = (boolean) floodgateApiClass.getMethod("isFloodgatePlayer", UUID.class)
                    .invoke(api, playerUuid);
            if (isFloodgate) {
                return true;
            }
        } catch (ClassNotFoundException e) {
            // Floodgate not installed
        } catch (Exception e) {
            logger.warning("Error checking Floodgate for " + player.getName() + ": " + e.getMessage());
        }

        // Try Geyser API
        try {
            Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object geyserApi = geyserApiClass.getMethod("api").invoke(null);

            if (geyserApi != null) {
                Object connection = geyserApiClass.getMethod("connectionByUuid", UUID.class)
                        .invoke(geyserApi, playerUuid);
                return connection != null;
            }
        } catch (ClassNotFoundException e) {
            // Geyser not installed
        } catch (Exception e) {
            logger.warning("Error checking Geyser for " + player.getName() + ": " + e.getMessage());
        }

        return false;
    }
}
