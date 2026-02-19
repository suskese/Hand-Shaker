package me.mklv.handshaker.neoforge.server;

import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.configs.ConfigRuntime.ModConfigStore;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigLoadOptions;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.Action;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.Behavior;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.IntegrityMode;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.ModConfig;
import me.mklv.handshaker.common.configs.ModChecks.ModCheckEvaluator;
import me.mklv.handshaker.common.configs.ModChecks.ModCheckInput;
import me.mklv.handshaker.common.configs.ModChecks.ModCheckResult;
import me.mklv.handshaker.common.utils.ClientInfo;
import net.neoforged.fml.loading.FMLPaths;
import java.io.*;
import java.util.*;

public class ConfigManager extends CommonConfigManagerBase {
    private final File configDir;

    public enum ModStatus { REQUIRED, ALLOWED, BLACKLISTED }

    public ConfigManager() {
        File configRootDir = FMLPaths.CONFIGDIR.get().toFile();
        this.configDir = new File(configRootDir, "HandShaker");
    }

    public java.nio.file.Path getConfigDirPath() {
        return configDir.toPath();
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
        loadCommon(configDir.toPath(), ConfigManager.class, bootstrapLogger, options);
    }

    public Map<String, String> getMessages() { return Collections.unmodifiableMap(messagesMap); }

    public boolean setModConfigByString(String modId, String mode, String action, String warnMessage) {
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
            optionalModsActive,
            "none",
            "kick"
        );
        save();
    }

    public void save() {
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

        saveCommon(configDir.toPath(), saveLogger);
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
            hashMods,
            modVersioning,
            collectKnownHashes(),
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

    private Map<String, String> collectKnownHashes() {
        if (!hashMods) {
            return Collections.emptyMap();
        }

        var serverMod = HandShakerServerMod.getInstance();
        if (serverMod == null || serverMod.getPlayerHistoryDb() == null) {
            return Collections.emptyMap();
        }

        Set<String> ruleKeys = new LinkedHashSet<>();
        ruleKeys.addAll(requiredModsActive);
        ruleKeys.addAll(blacklistedModsActive);
        ruleKeys.addAll(whitelistedModsActive);
        ruleKeys.addAll(optionalModsActive);
        return serverMod.getPlayerHistoryDb().getRegisteredHashes(ruleKeys, modVersioning);
    }
}
