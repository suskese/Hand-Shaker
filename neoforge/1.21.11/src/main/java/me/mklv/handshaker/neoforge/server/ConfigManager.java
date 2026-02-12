package me.mklv.handshaker.neoforge.server;

import me.mklv.handshaker.common.configs.ActionDefinition;
import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigSnapshotBuilder;
import me.mklv.handshaker.common.configs.ConfigWriter;
import me.mklv.handshaker.common.configs.ModConfigStore;
import me.mklv.handshaker.common.configs.ModCheckEvaluator;
import me.mklv.handshaker.common.configs.ModCheckInput;
import me.mklv.handshaker.common.configs.ModCheckResult;
import me.mklv.handshaker.common.configs.ConfigLoadOptions;
import me.mklv.handshaker.common.configs.ConfigLoadResult;
import me.mklv.handshaker.common.configs.ConfigLoader;
import me.mklv.handshaker.common.configs.ConfigState.Action;
import me.mklv.handshaker.common.configs.ConfigState.Behavior;
import me.mklv.handshaker.common.configs.ConfigState.IntegrityMode;
import me.mklv.handshaker.common.configs.ConfigState.ModConfig;
import me.mklv.handshaker.common.utils.ClientInfo;
import net.neoforged.fml.loading.FMLPaths;
import java.io.*;
import java.util.*;

public class ConfigManager {
    private final File configDir;

    public enum ModStatus { REQUIRED, ALLOWED, BLACKLISTED }

    private Behavior behavior = Behavior.STRICT;
    private IntegrityMode integrityMode = IntegrityMode.SIGNED;
    private String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
    private String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
    private String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
    private boolean allowBedrockPlayers = false;
    private boolean playerdbEnabled = false;
    private int handshakeTimeoutSeconds = 5;
    
    // Mod list toggle states
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
        File configRootDir = FMLPaths.CONFIGDIR.get().toFile();
        this.configDir = new File(configRootDir, "HandShaker");
    }

    public void load() {
        configDir.mkdirs();

        ConfigFileBootstrap.Logger bootstrapLogger = new ConfigFileBootstrap.Logger() {
            @Override
            public void info(String message) {
                HandShakerServerMod.LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                HandShakerServerMod.LOGGER.warn(message);
            }

            @Override
            public void error(String message, Throwable error) {
                HandShakerServerMod.LOGGER.error(message, error);
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
    public Map<String, String> getMessages() { return Collections.unmodifiableMap(messagesMap); }
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

    public void setDefaultMode(String value) {
        this.whitelist = value.equalsIgnoreCase("BLACKLISTED");
    }

    public String getDefaultMode() {
        return whitelist ? "BLACKLISTED" : "ALLOWED";
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

    public void setModsRequiredEnabledState(boolean enabled) {
        this.modsRequiredEnabled = enabled;
        save();
    }

    public void setModsBlacklistedEnabledState(boolean enabled) {
        this.modsBlacklistedEnabled = enabled;
        save();
    }

    public void setModsWhitelistedEnabledState(boolean enabled) {
        this.modsWhitelistedEnabled = enabled;
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

    public boolean isModIgnored(String modId) {
        return isIgnored(modId);
    }

    public boolean setModConfigByString(String modId, String mode, String action, String warnMessage) {
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

    public boolean setModConfig(String modId, ModStatus status, Action action, String warnMessage) {
        String statusStr = switch (status) {
            case REQUIRED -> "required";
            case BLACKLISTED -> "blacklisted";
            default -> "allowed";
        };
        String actionStr = action != null ? action.toString().toLowerCase() : "kick";
        return setModConfigByString(modId, statusStr, actionStr, warnMessage);
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

    public ModStatus getModStatus(String modId) {
        ModConfig cfg = getModConfig(modId);
        String mode = cfg.getMode().toLowerCase();
        return switch (mode) {
            case "required" -> ModStatus.REQUIRED;
            case "blacklisted" -> ModStatus.BLACKLISTED;
            default -> ModStatus.ALLOWED;
        };
    }

    public void addAllMods(Set<String> mods, ModStatus status) {
        String statusStr = switch (status) {
            case REQUIRED -> "required";
            case BLACKLISTED -> "blacklisted";
            default -> "allowed";
        };
        addAllModsStr(mods, statusStr, "kick", null);
    }

    private void addAllModsStr(Set<String> mods, String mode, String action, String warnMessage) {
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
                HandShakerServerMod.LOGGER.info(message);
            }

            @Override
            public void warn(String message) {
                HandShakerServerMod.LOGGER.warn(message);
            }

            @Override
            public void error(String message, Throwable error) {
                HandShakerServerMod.LOGGER.error(message, error);
            }
        };

        ConfigWriter.writeAll(configDir.toPath(), saveLogger, snapshot);
    }

    public void checkPlayer(net.minecraft.server.level.ServerPlayer player, ClientInfo info) {
        if (info == null) return;

        boolean hasMod = !info.mods().isEmpty();
        
        // Integrity Check - if mode is SIGNED, enforce signature verification
        if (integrityMode == IntegrityMode.SIGNED) {
            // If client has the handshaker mod, they MUST send valid integrity data
            if (hasMod) {
                // CRITICAL: If IntegrityPayload hasn't been received yet, KICK
                if (info.integrityNonce() == null) {
                    HandShakerServerMod.LOGGER.warn("Kicking {} - mod client but no integrity data sent in SIGNED mode", player.getName().getString());
                    player.connection.disconnect(net.minecraft.network.chat.Component.literal(invalidSignatureKickMessage));
                    return;
                } else if (!info.signatureVerified()) {
                    HandShakerServerMod.LOGGER.warn("Kicking {} - integrity check FAILED in SIGNED mode", player.getName().getString());
                    player.connection.disconnect(net.minecraft.network.chat.Component.literal(invalidSignatureKickMessage));
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
            player.connection.disconnect(net.minecraft.network.chat.Component.literal(noHandshakeKickMessage));
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
        if (result.isViolation() && result.getMessage() != null) {
            player.connection.disconnect(net.minecraft.network.chat.Component.literal(result.getMessage()));
        }
    }
}
