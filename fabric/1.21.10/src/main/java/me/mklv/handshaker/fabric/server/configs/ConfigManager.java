package me.mklv.handshaker.fabric.server.configs;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import me.mklv.handshaker.common.configs.ActionDefinition;
import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigSnapshotBuilder;
import me.mklv.handshaker.common.configs.ConfigWriter;
import me.mklv.handshaker.common.configs.MessagePlaceholderExpander;
import me.mklv.handshaker.common.configs.ModConfigStore;
import me.mklv.handshaker.common.configs.ModCheckEvaluator;
import me.mklv.handshaker.common.configs.ModCheckInput;
import me.mklv.handshaker.common.configs.ModCheckResult;
import me.mklv.handshaker.common.configs.ConfigLoadOptions;
import me.mklv.handshaker.common.configs.ConfigLoadResult;
import me.mklv.handshaker.common.configs.ConfigLoader;
import me.mklv.handshaker.common.configs.ConfigState.Behavior;
import me.mklv.handshaker.common.configs.ConfigState.IntegrityMode;
import me.mklv.handshaker.common.configs.ConfigState.ModConfig;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.fabric.server.HandShakerServer;
import me.mklv.handshaker.fabric.server.utils.PermissionsAdapter;

import java.io.*;
import java.util.*;

public class ConfigManager {
    private final File configDir;

    private Behavior behavior = Behavior.STRICT;
    private IntegrityMode integrityMode = IntegrityMode.SIGNED;
    private String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
    private String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
    private String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
    private boolean allowBedrockPlayers = false;
    private boolean playerdbEnabled = false; // Default: disabled for security
    private int handshakeTimeoutSeconds = 5;
    
    // Mod list toggle states - persisted in config
    private boolean modsRequiredEnabled = true;
    private boolean modsBlacklistedEnabled = true;
    private boolean modsWhitelistedEnabled = true;
    
    private final Map<String, ModConfig> modConfigMap = new LinkedHashMap<>();
    private boolean whitelist = false;
    private final Set<String> ignoredMods = new HashSet<>();
    private final Set<String> whitelistedModsActive = new HashSet<>();
    private final Set<String> optionalModsActive = new HashSet<>();
    private final Set<String> blacklistedModsActive = new HashSet<>();
    private final Set<String> requiredModsActive = new HashSet<>();
    private final Map<String, ActionDefinition> actionsMap = new LinkedHashMap<>();
    private final Map<String, String> messagesMap = new LinkedHashMap<>();

    public ConfigManager() {
        File configRootDir = FabricLoader.getInstance().getConfigDir().toFile();
        this.configDir = new File(configRootDir, "HandShaker");
    }

    public void load() {
        configDir.mkdirs();

        ConfigFileBootstrap.Logger bootstrapLogger = new ConfigFileBootstrap.Logger() {
            @Override
            public void info(String message) {
                HandShakerServer.LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                HandShakerServer.LOGGER.warn(message);
            }

            @Override
            public void error(String message, Throwable error) {
                HandShakerServer.LOGGER.error(message, error);
            }
        };

        ConfigLoadOptions options = new ConfigLoadOptions(true, true, true, "kick", true);
        ConfigLoadResult result = ConfigLoader.load(configDir.toPath(), ConfigManager.class, bootstrapLogger, options);
        applyLoadResult(result);
    }

    private void applyLoadResult(ConfigLoadResult result) {
        behavior = result.getBehavior();
        integrityMode = result.getIntegrityMode();
        kickMessage = result.getKickMessage();
        noHandshakeKickMessage = result.getNoHandshakeKickMessage();
        missingWhitelistModMessage = result.getMissingWhitelistModMessage();
        invalidSignatureKickMessage = result.getInvalidSignatureKickMessage();
        allowBedrockPlayers = result.isAllowBedrockPlayers();
        playerdbEnabled = result.isPlayerdbEnabled();
        handshakeTimeoutSeconds = result.getHandshakeTimeoutSeconds();
        modsRequiredEnabled = result.areModsRequiredEnabled();
        modsBlacklistedEnabled = result.areModsBlacklistedEnabled();
        modsWhitelistedEnabled = result.areModsWhitelistedEnabled();
        whitelist = result.isWhitelist();

        messagesMap.clear();
        messagesMap.putAll(result.getMessages());

        modConfigMap.clear();
        modConfigMap.putAll(result.getModConfigMap());
        ignoredMods.clear();
        ignoredMods.addAll(result.getIgnoredMods());
        whitelistedModsActive.clear();
        whitelistedModsActive.addAll(result.getWhitelistedModsActive());
        optionalModsActive.clear();
        optionalModsActive.addAll(result.getOptionalModsActive());
        blacklistedModsActive.clear();
        blacklistedModsActive.addAll(result.getBlacklistedModsActive());
        requiredModsActive.clear();
        requiredModsActive.addAll(result.getRequiredModsActive());
        actionsMap.clear();
        actionsMap.putAll(result.getActionsMap());
    }


    // Getters
    public Behavior getBehavior() { return behavior; }
    public IntegrityMode getIntegrityMode() { return integrityMode; }
    public String getKickMessage() { return kickMessage; }
    public String getNoHandshakeKickMessage() { return noHandshakeKickMessage; }
    public String getMissingWhitelistModMessage() { return missingWhitelistModMessage; }
    public String getInvalidSignatureKickMessage() { return invalidSignatureKickMessage; }
    public Map<String, ModConfig> getModConfigMap() { return Collections.unmodifiableMap(modConfigMap); }
    public boolean isWhitelist() { return whitelist; }
    public Set<String> getIgnoredMods() { return Collections.unmodifiableSet(ignoredMods); }
    public boolean isAllowBedrockPlayers() { return allowBedrockPlayers; }
    public Set<String> getWhitelistedMods() { return Collections.unmodifiableSet(whitelistedModsActive); }
    public Set<String> getOptionalMods() { return Collections.unmodifiableSet(optionalModsActive); }
    public Set<String> getBlacklistedMods() { return Collections.unmodifiableSet(blacklistedModsActive); }
    public Set<String> getRequiredMods() { return Collections.unmodifiableSet(requiredModsActive); }
    public boolean isPlayerdbEnabled() { return playerdbEnabled; }
    public boolean areModsRequiredEnabled() { return modsRequiredEnabled; }
    public boolean areModsBlacklistedEnabled() { return modsBlacklistedEnabled; }
    public boolean areModsWhitelistedEnabled() { return modsWhitelistedEnabled; }
    public int getHandshakeTimeoutSeconds() { return handshakeTimeoutSeconds; }
    public ActionDefinition getAction(String actionName) { 
        if (actionName == null) return null;
        return actionsMap.get(actionName.toLowerCase(Locale.ROOT));
    }
    public String getMessageOrDefault(String key, String fallback) {
        if (key == null) {
            return fallback;
        }
        String message = messagesMap.get(key);
        return message != null ? message : fallback;
    }
    public Set<String> getAvailableActions() {
        return Collections.unmodifiableSet(actionsMap.keySet());
    }

    // Setters for configuration
    public void setBehavior(String value) {
        this.behavior = value.equalsIgnoreCase("STRICT") ? Behavior.STRICT : Behavior.VANILLA;
    }

    public void setIntegrityMode(String value) {
        this.integrityMode = value.equalsIgnoreCase("SIGNED") ? IntegrityMode.SIGNED : IntegrityMode.DEV;
    }

    public void setWhitelist(boolean value) {
        this.whitelist = value;
    }

    public boolean toggleWhitelistedModsActive() {
        modsWhitelistedEnabled = !modsWhitelistedEnabled;
        if (modsWhitelistedEnabled) {
            loadWhitelistedModsFromFile();
        } else {
            whitelistedModsActive.clear();
        }
        save();
        return modsWhitelistedEnabled;
    }

    public boolean toggleBlacklistedModsActive() {
        modsBlacklistedEnabled = !modsBlacklistedEnabled;
        if (modsBlacklistedEnabled) {
            loadBlacklistedModsFromFile();
        } else {
            blacklistedModsActive.clear();
        }
        save();
        return modsBlacklistedEnabled;
    }

    public boolean toggleRequiredModsActive() {
        modsRequiredEnabled = !modsRequiredEnabled;
        if (modsRequiredEnabled) {
            loadRequiredModsFromFile();
        } else {
            requiredModsActive.clear();
        }
        save();
        return modsRequiredEnabled;
    }

    private void loadWhitelistedModsFromFile() {
        File whitelistedFile = new File(configDir, "mods-whitelisted.yml");
        if (whitelistedFile.exists()) {
            try (FileReader reader = new FileReader(whitelistedFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("whitelisted")) {
                    Object whitelistedObj = data.get("whitelisted");
                    if (whitelistedObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> whitelistedMap = (Map<String, Object>) whitelistedObj;
                        for (String mod : whitelistedMap.keySet()) {
                            whitelistedModsActive.add(mod.toLowerCase(Locale.ROOT));
                        }
                    } else if (whitelistedObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> whitelistedList = (List<String>) whitelistedObj;
                        if (whitelistedList != null) {
                            for (String mod : whitelistedList) {
                                whitelistedModsActive.add(mod.toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.warn("Failed to load mods-whitelisted.yml: {}", e.getMessage());
            }
        }
    }

    private void loadBlacklistedModsFromFile() {
        File blacklistedFile = new File(configDir, "mods-blacklisted.yml");
        if (blacklistedFile.exists()) {
            try (FileReader reader = new FileReader(blacklistedFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("blacklisted")) {
                    Object blacklistedObj = data.get("blacklisted");
                    if (blacklistedObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> blacklistedMap = (Map<String, Object>) blacklistedObj;
                        for (String mod : blacklistedMap.keySet()) {
                            blacklistedModsActive.add(mod.toLowerCase(Locale.ROOT));
                        }
                    } else if (blacklistedObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> blacklistedList = (List<String>) blacklistedObj;
                        if (blacklistedList != null) {
                            for (String mod : blacklistedList) {
                                blacklistedModsActive.add(mod.toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.warn("Failed to load mods-blacklisted.yml: {}", e.getMessage());
            }
        }
    }

    private void loadRequiredModsFromFile() {
        File requiredFile = new File(configDir, "mods-required.yml");
        if (requiredFile.exists()) {
            try (FileReader reader = new FileReader(requiredFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("required")) {
                    Object requiredObj = data.get("required");
                    if (requiredObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> requiredMap = (Map<String, Object>) requiredObj;
                        for (String mod : requiredMap.keySet()) {
                            requiredModsActive.add(mod.toLowerCase(Locale.ROOT));
                        }
                    } else if (requiredObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> requiredList = (List<String>) requiredObj;
                        if (requiredList != null) {
                            for (String mod : requiredList) {
                                requiredModsActive.add(mod.toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.warn("Failed to load mods-required.yml: {}", e.getMessage());
            }
        }
    }

    public void setKickMessage(String message) {
        this.kickMessage = message;
    }

    public void setNoHandshakeKickMessage(String message) {
        this.noHandshakeKickMessage = message;
    }

    public void setMissingWhitelistModMessage(String message) {
        this.missingWhitelistModMessage = message;
    }

    public void setInvalidSignatureKickMessage(String message) {
        this.invalidSignatureKickMessage = message;
    }

    public void setAllowBedrockPlayers(boolean allow) {
        this.allowBedrockPlayers = allow;
    }

    public void setPlayerdbEnabled(boolean enabled) {
        this.playerdbEnabled = enabled;
        save();
    }

    public void setHandshakeTimeoutSeconds(int seconds) {
        this.handshakeTimeoutSeconds = Math.max(1, seconds);
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
            modId,
            mode,
            action,
            warnMessage,
            null,
            null
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
            null,
            null
        );
        save();
    }


    public void save() {
        ConfigLoadResult snapshot = ConfigSnapshotBuilder.build(
            behavior,
            integrityMode,
            kickMessage,
            noHandshakeKickMessage,
            missingWhitelistModMessage,
            invalidSignatureKickMessage,
            allowBedrockPlayers,
            playerdbEnabled,
            modsRequiredEnabled,
            modsBlacklistedEnabled,
            modsWhitelistedEnabled,
            whitelist,
            handshakeTimeoutSeconds,
            messagesMap,
            modConfigMap,
            ignoredMods,
            whitelistedModsActive,
            blacklistedModsActive,
            requiredModsActive,
            optionalModsActive,
            actionsMap
        );

        ConfigFileBootstrap.Logger saveLogger = new ConfigFileBootstrap.Logger() {
            @Override
            public void info(String message) {
                HandShakerServer.LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                HandShakerServer.LOGGER.warn(message);
            }

            @Override
            public void error(String message, Throwable error) {
                HandShakerServer.LOGGER.error(message, error);
            }
        };

        ConfigWriter.writeAll(configDir.toPath(), saveLogger, snapshot);
    }

    public void checkPlayer(net.minecraft.server.network.ServerPlayerEntity player, ClientInfo info) {
        checkPlayer(player, info, true); // Execute actions by default
    }
    
    public void checkPlayer(net.minecraft.server.network.ServerPlayerEntity player, ClientInfo info, boolean executeActions) {
        if (info == null) return;

        // Check for bypass permission - allows players to bypass all mod checks
        if (PermissionsAdapter.checkPermission(player, "handshaker.bypass")) {
            return;
        }

        boolean hasMod = !info.mods().isEmpty();
        
        // Integrity Check - if mode is SIGNED, enforce signature verification
        // This is checked FIRST because it's the most critical security check
        if (integrityMode == IntegrityMode.SIGNED) {
            // If client has the handshaker mod, they MUST send valid integrity data
            if (hasMod) {
                // CRITICAL: If IntegrityPayload hasn't been received yet, KICK
                if (info.integrityNonce() == null) {
                    // Client has mod but never sent integrity payload - this is a security violation
                    HandShakerServer.LOGGER.warn("Kicking {} - mod client but no integrity data sent in SIGNED mode", player.getName().getString());
                    player.networkHandler.disconnect(net.minecraft.text.Text.literal(invalidSignatureKickMessage));
                    return;
                } else if (!info.signatureVerified()) {
                    // Client sent integrity data but verification FAILED
                    HandShakerServer.LOGGER.warn("Kicking {} - integrity check FAILED in SIGNED mode", player.getName().getString());
                    player.networkHandler.disconnect(net.minecraft.text.Text.literal(invalidSignatureKickMessage));
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
            player.networkHandler.disconnect(net.minecraft.text.Text.literal(noHandshakeKickMessage));
            return;
        }

        ModCheckInput input = new ModCheckInput(
            whitelist,
            modsRequiredEnabled,
            modsBlacklistedEnabled,
            modsWhitelistedEnabled,
            ignoredMods,
            whitelistedModsActive,
            optionalModsActive,
            blacklistedModsActive,
            requiredModsActive,
            modConfigMap,
            kickMessage,
            missingWhitelistModMessage
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
                player.networkHandler.disconnect(net.minecraft.text.Text.literal(result.getMessage()));
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

    public void playerLeft(ServerPlayerEntity player) {
    }

}
