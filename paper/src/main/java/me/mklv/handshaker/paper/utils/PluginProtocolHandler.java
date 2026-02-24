package me.mklv.handshaker.paper.utils;

import me.mklv.handshaker.paper.ConfigManager;
import me.mklv.handshaker.paper.HandShakerPlugin;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ConfigTypes.ActionDefinition;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.protocols.BedrockPlayer;
import me.mklv.handshaker.common.protocols.CertLoader;
import me.mklv.handshaker.common.protocols.PayloadValidation;
import me.mklv.handshaker.common.protocols.PayloadValidation.PayloadValidationCallbacks;
import me.mklv.handshaker.common.protocols.PayloadValidation.ValidationResult;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.SignatureVerifier;
import me.mklv.handshaker.common.utils.ModChecks.ModCheckResult;
import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.security.PublicKey;
import java.util.*;
import java.util.logging.Logger;

public class PluginProtocolHandler {
    private final HandShakerPlugin plugin;
    private final Map<UUID, ClientInfo> clients;
    private final Logger logger;
    private final SignatureVerifier signatureVerifier;
    private final PayloadDecoder payloadDecoder;
    private final ConfigManager configManager;
    private final PayloadValidation payloadValidator;

    public PluginProtocolHandler(HandShakerPlugin plugin, Map<UUID, ClientInfo> clients) {
        this.plugin = plugin;
        this.clients = clients;
        this.logger = plugin.getLogger();
        this.configManager = plugin.getConfigManager();
        this.payloadDecoder = new PayloadDecoder(logger);
        
        // Load public key for signature verification
        PublicKey publicKey = loadPublicCertificate();
        this.signatureVerifier = new SignatureVerifier(publicKey, new SignatureVerifier.LogSink() {
            @Override
            public void info(String message) {
                if (HandShakerPlugin.DEBUG) {
                    logger.info(message);
                }
            }

            @Override
            public void warn(String message) {
                logger.warning(message);
            }
        });

        // Initialize unified payload validator with Paper-specific callbacks
        this.payloadValidator = new PayloadValidation(
            new PayloadValidationCallbacks() {
                @Override
                public String getMessageOrDefault(String key, String defaultMessage) {
                    return configManager.getMessageOrDefault(key, defaultMessage);
                }

                @Override
                public void logInfo(String format, Object... args) {
                    logger.info(String.format(format, args));
                }

                @Override
                public void logWarning(String format, Object... args) {
                    logger.warning(String.format(format, args));
                }

                @Override
                public void syncPlayerMods(UUID playerId, String playerName, Set<String> mods) {
                    PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
                    if (db != null) {
                        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                            try {
                                db.syncPlayerMods(playerId, playerName, mods);
                            } catch (Exception e) {
                                logger.warning("Failed to sync player mods to database: " + e.getMessage());
                            }
                        });
                    }
                }

                @Override
                public void checkPlayer(UUID playerId, String playerName, ClientInfo info) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        clients.put(playerId, info);
                        PluginProtocolHandler.this.checkPlayer(player, clients);
                    }
                }
            },
            signatureVerifier,
            clients
        );
        payloadValidator.setDebugMode(HandShakerPlugin.DEBUG);
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
        return CertLoader.loadPublicKey(plugin.getClass(), "/public.cer", new CertLoader.LogSink() {
            @Override
            public void info(String message) {
                if (HandShakerPlugin.DEBUG) {
                    logger.info(message);
                }
            }

            @Override
            public void warn(String message) {
                logger.warning(message);
            }
        });
    }

    private void handleModList(Player player, byte[] data) {
        try {
            String payload = payloadDecoder.decodeString(data);
            if (payload == null || payload.isBlank()) {
                logger.warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }

            // Decode hash
            PayloadDecoder.DecodeResult hashResult = payloadDecoder.decodeStringWithOffset(data, calculateOffset(data, payload.length()));
            if (hashResult == null || hashResult.value == null || ((String) hashResult.value).isEmpty()) {
                logger.warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String modListHash = (String) hashResult.value;

            // Decode nonce
            PayloadDecoder.DecodeResult nonceResult = payloadDecoder.decodeStringWithOffset(data, hashResult.offset);
            if (nonceResult == null || nonceResult.value == null || ((String) nonceResult.value).isEmpty()) {
                logger.warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String nonce = (String) nonceResult.value;

            // Ensure fabric flag is set for this player
            clients.putIfAbsent(player.getUniqueId(), new ClientInfo(
                true, new HashSet<>(), false, false, null, null, null, false));

            // Delegate to unified validator
            ValidationResult result = payloadValidator.validateModList(player.getUniqueId(), player.getName(), 
                payload, modListHash, nonce);
            if (!result.success) {
                kickPlayer(player, result.errorMessage);
            }
        } catch (Exception e) {
            logger.severe("Failed to decode mod list from " + player.getName() + ". Terminating connection: " + e.getMessage());
            kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                StandardMessages.HANDSHAKE_CORRUPTED));
        }
    }

    private void handleIntegrityPayload(Player player, byte[] data) {
        try {
            // Decode signature (byte array)
            PayloadDecoder.DecodeResult sigResult = payloadDecoder.decodeByteArrayWithOffset(data, 0);
            if (sigResult == null) {
                logger.warning("Failed to decode integrity payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            byte[] clientSignature = (byte[]) sigResult.value;

            // Decode jar hash (string)
            PayloadDecoder.DecodeResult hashResult = payloadDecoder.decodeStringWithOffset(data, sigResult.offset);
            if (hashResult == null || hashResult.value == null) {
                logger.warning("Failed to decode integrity payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String jarHash = (String) hashResult.value;

            // Decode nonce (string)
            PayloadDecoder.DecodeResult nonceResult = payloadDecoder.decodeStringWithOffset(data, hashResult.offset);
            if (nonceResult == null || nonceResult.value == null) {
                logger.warning("Failed to decode integrity payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String nonce = (String) nonceResult.value;

            // Ensure fabric flag is set for this player
            clients.putIfAbsent(player.getUniqueId(), new ClientInfo(
                true, new HashSet<>(), false, false, null, null, null, false));

            // Delegate to unified validator
            ValidationResult result = payloadValidator.validateIntegrity(player.getUniqueId(), player.getName(), 
                clientSignature, jarHash, nonce);
            if (!result.success) {
                kickPlayer(player, result.errorMessage);
            }
        } catch (Exception e) {
            logger.severe("Failed to decode integrity payload from " + player.getName() + ". Terminating connection: " + e.getMessage());
            kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                StandardMessages.HANDSHAKE_CORRUPTED));
        }
    }

    private void handleVeltonPayloadInternal(Player player, byte[] data) {
        try {
            // Decode jar hash (string) - this is the signature verification result from Velton
            PayloadDecoder.DecodeResult hashResult = payloadDecoder.decodeStringWithOffset(data, 0);
            if (hashResult == null || hashResult.value == null) {
                logger.warning("Failed to decode Velton payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String jarHash = (String) hashResult.value;

            // Decode nonce (string)
            PayloadDecoder.DecodeResult nonceResult = payloadDecoder.decodeStringWithOffset(data, hashResult.offset);
            if (nonceResult == null || nonceResult.value == null) {
                logger.warning("Failed to decode Velton payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String nonce = (String) nonceResult.value;

            // Ensure fabric flag is set for this player
            clients.putIfAbsent(player.getUniqueId(), new ClientInfo(
                true, new HashSet<>(), false, false, null, null, null, false));

            // Delegate to unified validator
            ValidationResult result = payloadValidator.validateVelton(player.getUniqueId(), player.getName(), 
                jarHash, nonce);
            if (!result.success) {
                kickPlayer(player, result.errorMessage);
            }
        } catch (Exception e) {
            logger.severe("Failed to decode Velton payload from " + player.getName() + ". Terminating connection: " + e.getMessage());
            kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                StandardMessages.HANDSHAKE_CORRUPTED));
        }
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
        if (configManager.getBehavior() == ConfigState.Behavior.STRICT && !info.fabric()) {
            kickPlayer(player, configManager.getNoHandshakeKickMessage());
            return;
        }

        // Integrity Check (only if client has the mod or behavior is STRICT)
        if (info.fabric() && configManager.getIntegrityMode() == ConfigState.IntegrityMode.SIGNED) {
            if (!info.signatureVerified()) {
                kickPlayer(player, configManager.getInvalidSignatureKickMessage());
                return;
            }
        }

        Set<String> mods = info.mods();

        // Check player and execute action if needed
        ModCheckResult status = configManager.checkPlayerWithAction(player, mods);
        
        if (HandShakerPlugin.DEBUG) {
            logger.info("[DEBUG] checkPlayerWithAction returned: " + (status != null ? "status(" + status.getActionName() + ")" : "null"));
        }
        
        if (status != null) {
            // Execute action first (if any), then kick if this is a violation.
            // This allows blacklist/required/whitelist actions (ban, log, custom commands) to run.
            if (player.isOnline()) {
                if (HandShakerPlugin.DEBUG) {
                    logger.info("[DEBUG] Executing action for " + player.getName() + ": " + status.getActionName());
                }
                executeAction(player, status.getActionName(), status.getMods());
            }

            if (status.isViolation()) {
                if (HandShakerPlugin.DEBUG) {
                    logger.info("[DEBUG] Player " + player.getName() + " has violation, kicking");
                }
                kickPlayer(player, status.getMessage());
                return;
            }
        }

        // Mark as checked to prevent double execution
        clients.put(player.getUniqueId(), info.withChecked(true));
        
        // Log timing in debug mode
        if (HandShakerPlugin.DEBUG) {
            Long joinTime = plugin.getJoinTimestamp(player.getUniqueId());
            if (joinTime != null) {
                long elapsed = System.currentTimeMillis() - joinTime;
                logger.info("[TIMER] Player " + player.getName() + " fully checked in " + elapsed + "ms");
                plugin.removeJoinTimestamp(player.getUniqueId());
            }
        }
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

    private boolean isBedrockPlayer(Player player) {
        return BedrockPlayer.isBedrockPlayer(player.getUniqueId(), player.getName(), new BedrockPlayer.LogSink() {
            @Override
            public void warn(String message) {
                logger.warning(message);
            }
        });
    }

}
