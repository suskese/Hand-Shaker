package me.mklv.handshaker.fabric.server.configs;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.configs.ConfigRuntime.MessagePlaceholderExpander;
import me.mklv.handshaker.common.configs.ConfigRuntime.ModConfigStore;
import me.mklv.handshaker.common.configs.ConfigTypes.ActionDefinition;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigLoadOptions;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.Behavior;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.IntegrityMode;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.ModConfig;
import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.protocols.BedrockPlayer;
import me.mklv.handshaker.common.protocols.CollectKnownHashes;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import me.mklv.handshaker.common.utils.ModCache;
import me.mklv.handshaker.common.utils.ModChecks.ModCheckEvaluator;
import me.mklv.handshaker.common.utils.ModChecks.ModCheckInput;
import me.mklv.handshaker.common.utils.ModChecks.ModCheckResult;
import me.mklv.handshaker.fabric.server.HandShakerServer;
import me.mklv.handshaker.fabric.server.utils.PermissionsAdapter;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class ConfigManager extends CommonConfigManagerBase {
    private final File configDir;

    public ConfigManager() {
        File configRootDir = FabricLoader.getInstance().getConfigDir().toFile();
        this.configDir = new File(configRootDir, "HandShaker");
    }

    public Path getConfigDirPath() {
        return configDir.toPath();
    }

    public void load() {
        configDir.mkdirs();

        ConfigFileBootstrap.Logger bootstrapLogger = LoggerAdapter.fromLoaderLogger(HandShakerServer.LOGGER);

        ConfigLoadOptions options = new ConfigLoadOptions(true, true, true, "kick", true);
        loadCommon(configDir.toPath(), ConfigManager.class, bootstrapLogger, options);
        ModCache.invalidate();

        HandShakerServer.DEBUG_MODE = isDebug();
    }

    public boolean toggleWhitelistedModsActive() {
        modsWhitelistedEnabled = !modsWhitelistedEnabled;
        save();
        return modsWhitelistedEnabled;
    }

    public boolean toggleBlacklistedModsActive() {
        modsBlacklistedEnabled = !modsBlacklistedEnabled;
        save();
        return modsBlacklistedEnabled;
    }

    public boolean toggleRequiredModsActive() {
        modsRequiredEnabled = !modsRequiredEnabled;
        save();
        return modsRequiredEnabled;
    }

    public void setKickMessage(String message) {
        super.setKickMessage(message);
    }

    public void setNoHandshakeKickMessage(String message) {
        super.setNoHandshakeKickMessage(message);
    }

    public void setMissingWhitelistModMessage(String message) {
        super.setMissingWhitelistModMessage(message);
    }

    public void setInvalidSignatureKickMessage(String message) {
        super.setInvalidSignatureKickMessage(message);
    }

    public void setAllowBedrockPlayers(boolean allow) {
        super.setAllowBedrockPlayers(allow);
    }

    public void setPlayerdbEnabled(boolean enabled) {
        this.playerdbEnabled = enabled;
        save();
    }

    public void setHandshakeTimeoutSeconds(int seconds) {
        super.setHandshakeTimeoutSeconds(seconds);
        save();
    }

    public void setHashMods(boolean enabled) {
        this.hashMods = enabled;
        save();
    }

    public void setModVersioning(boolean enabled) {
        this.modVersioning = enabled;
        save();
    }

    public boolean addIgnoredMod(String modId) {
        if (ignoredMods.add(modId.toLowerCase(Locale.ROOT))) {
            save();
            return true;
        }
        return false;
    }

    public boolean removeIgnoredMod(String modId) {
        if (ignoredMods.remove(modId.toLowerCase(Locale.ROOT))) {
            save();
            return true;
        }
        return false;
    }

    public boolean isIgnored(String modId) {
        return ignoredMods.contains(modId.toLowerCase(Locale.ROOT));
    }

    public boolean setModConfig(String modId, String mode, String action, String warnMessage) {
        ModConfigStore.upsertModConfig(
            modConfigMap,
            requiredModsActive,
            blacklistedModsActive,
            whitelistedModsActive,
            optionalModsActive,
            modId,
            mode,
            action,
            warnMessage,
            "none",
            "kick"
        );
        save();
        return true;
    }

    public boolean removeModConfig(String modId) {
        boolean removed = ModConfigStore.removeModConfig(
            modConfigMap,
            requiredModsActive,
            blacklistedModsActive,
            whitelistedModsActive,
            optionalModsActive,
            modId
        );
        if (removed) {
            save();
        }
        return removed;
    }

    public ModConfig getModConfig(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        ModConfig cfg = modConfigMap.get(modId);
        if (cfg != null) return cfg;
        // Default behavior based on whitelist mode
        String defaultModeStr = whitelist ? "blacklisted" : "allowed";
        return new ModConfig(defaultModeStr, "kick", null);
    }

    public void addAllMods(Set<String> mods, String mode, String action, String warnMessage) {
        ModConfigStore.addAllMods(
            mods,
            mode,
            action,
            warnMessage,
            modConfigMap,
            requiredModsActive,
            blacklistedModsActive,
            whitelistedModsActive,
            optionalModsActive,
            "none",
            "kick"
        );
        save();
    }


    public void save() {
        ConfigFileBootstrap.Logger saveLogger = LoggerAdapter.fromLoaderLogger(HandShakerServer.LOGGER);

        saveCommon(configDir.toPath(), saveLogger);
    }

    public void checkPlayer(ServerPlayerEntity player, ClientInfo info) {
        checkPlayer(player, info, true, false); // Execute actions by default, not a timeout check
    }
    
    public void checkPlayer(ServerPlayerEntity player, ClientInfo info, boolean executeActions) {
        checkPlayer(player, info, executeActions, false); // Not a timeout check
    }
    
    public void checkPlayer(ServerPlayerEntity player, ClientInfo info, boolean executeActions, boolean isTimeoutCheck) {
        // Check for bypass permission - allows players to bypass all mod checks
        if (PermissionsAdapter.checkPermission(player, "handshaker.bypass")) {
            return;
        }
        
        boolean bedrockPlayer = BedrockPlayer.isBedrockPlayer(
            player.getUuid(),
            player.getName().getString(),
            message -> HandShakerServer.LOGGER.warn(message)
        );
        if (bedrockPlayer) {
            if (allowBedrockPlayers) {
                return;
            }
            player.networkHandler.disconnect(Text.literal(getMessageOrDefault(
                StandardMessages.KEY_BEDROCK,
                StandardMessages.DEFAULT_BEDROCK_MESSAGE
            )));
            return;
        }

        boolean hasMod = info != null && !info.mods().isEmpty();
        
        // Integrity Check - if mode is SIGNED, enforce signature verification
        // This is checked FIRST because it's the most critical security check
        if (integrityMode == IntegrityMode.SIGNED) {
            // If client has the handshaker mod, they MUST send valid integrity data
            if (hasMod) {
                // Only enforce integrityNonce requirement if:
                // 1. This is a timeout check (player has had time to send integrity), OR
                // 2. IntegrityNonce was already received (client sent both payloads)
                // During initial mod list reception, we allow time for integrity payload to arrive
                if (info.integrityNonce() == null && isTimeoutCheck) {
                    // Timeout check and no integrity data received - this is a security violation
                    HandShakerServer.LOGGER.warn("Kicking {} - mod client but no integrity data sent in SIGNED mode after waiting", player.getName().getString());
                    player.networkHandler.disconnect(Text.literal(invalidSignatureKickMessage));
                    return;
                } else if (info.integrityNonce() != null && !info.signatureVerified()) {
                    // Client sent integrity data but verification FAILED
                    HandShakerServer.LOGGER.warn("Kicking {} - integrity check FAILED in SIGNED mode", player.getName().getString());
                    player.networkHandler.disconnect(Text.literal(invalidSignatureKickMessage));
                    return;
                }
            }
        }
        
        // If behavior is VANILLA and client doesn't have the mod, skip all checks
        if (behavior == Behavior.VANILLA && !hasMod) {
            return;
        }
        
        // If behavior is STRICT and client doesn't have the mod, kick
        if (behavior == Behavior.STRICT && !hasMod) {
            player.networkHandler.disconnect(Text.literal(noHandshakeKickMessage));
            return;
        }

        if (!hasMod) {
            return;
        }

        ModCheckInput input = new ModCheckInput(
            whitelist,
            modsRequiredEnabled,
            modsBlacklistedEnabled,
            modsWhitelistedEnabled,
            hashMods,
            modVersioning,
            isHybridCompatibilityEnabled(),
            getRequiredModpackHash(),
            collectKnownHashes(),
            ignoredMods,
            whitelistedModsActive,
            optionalModsActive,
            blacklistedModsActive,
            requiredModsActive,
            modConfigMap,
            kickMessage,
            missingWhitelistModMessage,
            getMessageOrDefault(StandardMessages.KEY_MODPACK_HASH_MISMATCH, StandardMessages.MODPACK_HASH_MISMATCH)
        );
        ModCheckResult result = ModCheckEvaluator.evaluate(input, info.mods());

        if (result.isViolation()) {
            if (result.isBlacklistedViolation()) {
                String actionName = result.getActionName() != null
                    ? result.getActionName().toLowerCase(Locale.ROOT)
                    : "kick";
                ActionDefinition actionDef = actionsMap.get(actionName);
                if (actionDef != null && !actionDef.isEmpty()) {
                    if (actionDef.shouldLog()) {
                        HandShakerServer.LOGGER.info("Executing action '{}' for player {} (blacklisted mods: {})",
                            actionName, player.getName().getString(), result.getMods());
                    }

                    String modList = String.join(", ", result.getMods());
                    for (String command : actionDef.getCommands()) {
                        String expandedCommand = MessagePlaceholderExpander.expand(
                            command,
                            player.getName().getString(),
                            modList,
                            messagesMap
                        );
                        MinecraftServer server = HandShakerServer.getInstance().getServer();
                        if (server != null) {
                            try {
                                var parseResults = server.getCommandManager().getDispatcher().parse(expandedCommand, server.getCommandSource());
                                server.getCommandManager().execute(parseResults, expandedCommand);
                            } catch (Exception e) {
                                HandShakerServer.LOGGER.warn("Failed to execute action command '{}': {}", expandedCommand, e.getMessage());
                            }
                        } else {
                            HandShakerServer.LOGGER.warn("Server instance is null, cannot execute action command");
                        }
                    }
                }
            }

            if (result.getMessage() != null) {
                player.networkHandler.disconnect(Text.literal(result.getMessage()));
            }
            return;
        }

        if (executeActions && modsWhitelistedEnabled && result.hasAllowedActions()) {
            for (Map.Entry<String, String> entry : result.getAllowedActionsByMod().entrySet()) {
                String allowedMod = entry.getKey();
                String actionName = entry.getValue();
                ActionDefinition actionDef = actionsMap.get(actionName.toLowerCase(Locale.ROOT));
                if (HandShakerServer.DEBUG_MODE) {
                    HandShakerServer.LOGGER.info("Allowed mod detected: {}. Action: '{}'. ActionDef exists: {}. ActionDef empty: {}",
                        allowedMod, actionName, actionDef != null, (actionDef != null && actionDef.isEmpty()));
                }

                if (actionDef != null && !actionDef.isEmpty()) {
                    if (actionDef.shouldLog()) {
                        HandShakerServer.LOGGER.info("Executing action '{}' for player {} (allowed mod: {})",
                            actionName, player.getName().getString(), allowedMod);
                    }

                    for (String command : actionDef.getCommands()) {
                        String expandedCommand = MessagePlaceholderExpander.expand(
                            command,
                            player.getName().getString(),
                            allowedMod,
                            messagesMap
                        );
                        MinecraftServer server = HandShakerServer.getInstance().getServer();
                        if (server != null) {
                            try {
                                var parseResults = server.getCommandManager().getDispatcher().parse(expandedCommand, server.getCommandSource());
                                server.getCommandManager().execute(parseResults, expandedCommand);
                            } catch (Exception e) {
                                HandShakerServer.LOGGER.warn("Failed to execute action command '{}': {}", expandedCommand, e.getMessage());
                            }
                        } else {
                            HandShakerServer.LOGGER.warn("Server instance is null, cannot execute action command");
                        }
                    }

                }
            }
        }
    }

    private Map<String, String> collectKnownHashes() {
        if (!hashMods) {
            return Collections.emptyMap();
        }

        var server = HandShakerServer.getInstance();
        if (server == null || server.getPlayerHistoryDb() == null) {
            return Collections.emptyMap();
        }

        PlayerHistoryDatabase db = server.getPlayerHistoryDb();
        return CollectKnownHashes.collect(
            hashMods,
            runtimeCache,
            db,
            modVersioning,
            requiredModsActive,
            blacklistedModsActive,
            whitelistedModsActive,
            optionalModsActive
        );
    }

    public void playerLeft(ServerPlayerEntity player) {
    }

}
