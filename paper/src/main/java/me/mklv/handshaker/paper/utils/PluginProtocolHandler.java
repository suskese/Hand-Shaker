package me.mklv.handshaker.paper.utils;

import me.mklv.handshaker.paper.ConfigManager;
import me.mklv.handshaker.paper.HandShakerPlugin;
import me.mklv.handshaker.common.configs.CommonPlayerCheckEngine;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.protocols.CertLoader;
import me.mklv.handshaker.common.protocols.PayloadValidation;
import me.mklv.handshaker.common.protocols.PayloadValidation.PayloadValidationCallbacks;
import me.mklv.handshaker.common.protocols.PayloadValidation.ValidationResult;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.SignatureVerifier;
import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.security.PublicKey;
import java.util.*;
import java.util.regex.Pattern;
import java.util.logging.Logger;

public class PluginProtocolHandler {
    private static final Pattern SHA256_HEX = Pattern.compile("^[a-f0-9]{64}$");

    private final HandShakerPlugin plugin;
    private final Logger logger;
    private final SignatureVerifier signatureVerifier;
    private final PayloadDecoder payloadDecoder;
    private final ConfigManager configManager;
    private final PayloadValidation payloadValidator;

    public PluginProtocolHandler(HandShakerPlugin plugin, Map<UUID, ClientInfo> clients) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.configManager = plugin.getConfigManager();
        this.payloadDecoder = new PayloadDecoder(logger);
        
        // Load public key for signature verification
        PublicKey publicKey = loadPublicCertificate();
        configManager.setSignatureVerificationAvailable(publicKey != null);
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
                public boolean isModernCompatibilityEnabled() {
                    return configManager.isModernCompatibilityEnabled();
                }

                @Override
                public boolean isHybridCompatibilityEnabled() {
                    return configManager.isHybridCompatibilityEnabled();
                }

                @Override
                public boolean isLegacyCompatibilityEnabled() {
                    return configManager.isLegacyCompatibilityEnabled();
                }

                @Override
                public boolean isUnsignedCompatibilityEnabled() {
                    return configManager.isUnsignedCompatibilityEnabled();
                }

                @Override
                public boolean isRateLimitEnabled() {
                    return configManager.getRateLimitPerMinute() > 0;
                }

                @Override
                public int getRateLimitPerMinute() {
                    return configManager.getRateLimitPerMinute();
                }

                @Override
                public boolean isPayloadCompressionEnabled() {
                    return configManager.isPayloadCompressionEnabled();
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

    public void clearNonceHistory(UUID playerId) {
        payloadValidator.clearNonceHistory(playerId);
    }

    public PayloadValidation getPayloadValidator() {
        return payloadValidator;
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
            PayloadDecoder.DecodeResult payloadResult = payloadDecoder.decodeStringWithOffset(data, 0);
            if (payloadResult == null || payloadResult.value == null) {
                logger.warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String payload = (String) payloadResult.value;
            if (payload.isBlank()) {
                logger.warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }

            PayloadDecoder.DecodeResult secondResult = payloadDecoder.decodeStringWithOffset(data, payloadResult.offset);
            if (secondResult == null || secondResult.value == null || ((String) secondResult.value).isEmpty()) {
                logger.warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String secondValue = (String) secondResult.value;

            // Modern: mods + hash + nonce
            // Legacy (3-5): mods + nonce
            PayloadDecoder.DecodeResult thirdResult = payloadDecoder.decodeStringWithOffset(data, secondResult.offset);

            String modListHash;
            String nonce;
            if (thirdResult != null && thirdResult.value != null && !((String) thirdResult.value).isEmpty()) {
                modListHash = secondValue;
                nonce = (String) thirdResult.value;
                if (HandShakerPlugin.DEBUG) {
                    logger.info("[DEBUG] Decoded mod list payload from " + player.getName() + " using MODERN format (mods+hash+nonce)");
                }
            } else {
                if (isSha256Hex(secondValue)) {
                    logger.warning("Failed to decode mod list from " + player.getName() + ": modern payload missing nonce.");
                    kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                        StandardMessages.HANDSHAKE_CORRUPTED));
                    return;
                }
                modListHash = "";
                nonce = secondValue;
                if (HandShakerPlugin.DEBUG) {
                    logger.info("[DEBUG] Decoded mod list payload from " + player.getName() + " using LEGACY format (mods+nonce)");
                }
            }

            if (nonce == null || nonce.isEmpty()) {
                logger.warning("Failed to decode mod list from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }

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

            PayloadDecoder.DecodeResult secondResult = payloadDecoder.decodeStringWithOffset(data, sigResult.offset);
            if (secondResult == null || secondResult.value == null) {
                logger.warning("Failed to decode integrity payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }
            String secondValue = (String) secondResult.value;

            // Modern/hybrid: signature + jarHash + nonce
            // Legacy (3-5): signature + nonce
            PayloadDecoder.DecodeResult thirdResult = payloadDecoder.decodeStringWithOffset(data, secondResult.offset);

            String jarHash;
            String nonce;
            if (thirdResult != null && thirdResult.value != null && !((String) thirdResult.value).isEmpty()) {
                jarHash = secondValue;
                nonce = (String) thirdResult.value;
                if (HandShakerPlugin.DEBUG) {
                    logger.info("[DEBUG] Decoded integrity payload from " + player.getName() + " using MODERN format (signature+jarHash+nonce)");
                }
            } else {
                if (isSha256Hex(secondValue)) {
                    logger.warning("Failed to decode integrity payload from " + player.getName() + ": modern payload missing nonce.");
                    kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                        StandardMessages.HANDSHAKE_CORRUPTED));
                    return;
                }
                jarHash = "";
                nonce = secondValue;
                if (HandShakerPlugin.DEBUG) {
                    logger.info("[DEBUG] Decoded integrity payload from " + player.getName() + " using LEGACY format (signature+nonce)");
                }
            }

            if (nonce == null || nonce.isEmpty()) {
                logger.warning("Failed to decode integrity payload from " + player.getName() + ". Rejecting.");
                kickPlayer(player, configManager.getMessageOrDefault(StandardMessages.KEY_HANDSHAKE_CORRUPTED,
                    StandardMessages.HANDSHAKE_CORRUPTED));
                return;
            }

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
        checkPlayer(player, clients, false); // Not a timeout check by default
    }

    public void checkPlayer(Player player, Map<UUID, ClientInfo> clients, boolean isTimeoutCheck) {
        ClientInfo info = clients.getOrDefault(
            player.getUniqueId(),
            new ClientInfo(Collections.emptySet(), false, false, null, null, null)
        );

        CommonPlayerCheckEngine.checkPlayer(
            configManager,
            player.getUniqueId(),
            player.getName(),
            info,
            !isTimeoutCheck,
            isTimeoutCheck,
            configManager.collectKnownHashesForChecks(),
            new CommonPlayerCheckEngine.Bridge() {
                @Override
                public void info(String message) {
                    logger.info(message);
                }

                @Override
                public void warn(String message) {
                    logger.warning(message);
                }

                @Override
                public void disconnect(String message) {
                    player.kick(Component.text(message).color(NamedTextColor.RED));
                }

                @Override
                public void executeServerCommand(String command) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }

                @Override
                public void publishBan(String playerName, String reason, String mods) {
                    plugin.publishWebhookBan(playerName, reason, mods);
                }

                @Override
                public void publishKick(String playerName, String reason, String mods) {
                    plugin.publishWebhookKick(playerName, reason, mods);
                }

                @Override
                public boolean hasBypassPermission() {
                    return player.hasPermission("handshaker.bypass");
                }

                @Override
                public Collection<ClassLoader> additionalBedrockClassLoaders() {
                    return getBedrockClassLoaders();
                }
            }
        );
        
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

    private void kickPlayer(Player player, String message) {
        plugin.publishWebhookKick(player.getName(), message, "");
        player.kick(Component.text(message).color(NamedTextColor.RED));
    }

    private boolean isSha256Hex(String value) {
        if (value == null) {
            return false;
        }
        return SHA256_HEX.matcher(value.trim().toLowerCase(Locale.ROOT)).matches();
    }

    private Collection<ClassLoader> getBedrockClassLoaders() {
        List<ClassLoader> classLoaders = new ArrayList<>(2);

        Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) {
            floodgate = Bukkit.getPluginManager().getPlugin("Floodgate");
        }
        if (floodgate != null && floodgate.isEnabled()) {
            classLoaders.add(floodgate.getClass().getClassLoader());
        }

        Plugin geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if (geyser == null || !geyser.isEnabled()) {
            geyser = Bukkit.getPluginManager().getPlugin("Geyser");
        }
        if (geyser != null && geyser.isEnabled()) {
            classLoaders.add(geyser.getClass().getClassLoader());
        }

        return classLoaders;
    }

}
