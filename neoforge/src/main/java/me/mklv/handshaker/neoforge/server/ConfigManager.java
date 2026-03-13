package me.mklv.handshaker.neoforge.server;

import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.CommonPlayerCheckEngine;
import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.configs.ConfigRuntime.ModConfigStore;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigLoadOptions;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.Action;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.ModConfig;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import me.mklv.handshaker.common.protocols.CollectKnownHashes;
import me.mklv.handshaker.common.utils.ModCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
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

        ConfigFileBootstrap.Logger bootstrapLogger = LoggerAdapter.fromLoaderLogger(HandShakerServerMod.LOGGER);

        ConfigLoadOptions options = new ConfigLoadOptions(true, false, true, "none", false);
        loadCommon(configDir.toPath(), ConfigManager.class, bootstrapLogger, options);
        ModCache.invalidate();
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
        String actionStr = action != null ? action.toString().toLowerCase(Locale.ROOT) : "kick";
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
        String defaultModeStr = whitelist ? "blacklisted" : "allowed";
        return new ModConfig(defaultModeStr, "kick", null);
    }

    public ModStatus getModStatus(String modId) {
        ModConfig cfg = getModConfig(modId);
        String mode = cfg.getMode().toLowerCase(Locale.ROOT);
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
        ConfigFileBootstrap.Logger saveLogger = LoggerAdapter.fromLoaderLogger(HandShakerServerMod.LOGGER);

        saveCommon(configDir.toPath(), saveLogger);
    }

    @SuppressWarnings("null")
    public void checkPlayer(ServerPlayer player, ClientInfo info) {
        CommonPlayerCheckEngine.checkPlayer(
            this,
            player.getUUID(),
            player.getName().getString(),
            info,
            false,
            true,
            collectKnownHashes(),
            new CommonPlayerCheckEngine.Bridge() {
                @Override
                public void info(String message) {
                    HandShakerServerMod.LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    HandShakerServerMod.LOGGER.warn(message);
                }

                @Override
                public void disconnect(String message) {
                    player.connection.disconnect(net.minecraft.network.chat.Component.literal(message));
                }

                @Override
                public void executeServerCommand(String command) {
                    HandShakerServerMod serverMod = HandShakerServerMod.getInstance();
                    if (serverMod == null || serverMod.getServer() == null) {
                        HandShakerServerMod.LOGGER.warn("Server instance is null, cannot execute action command");
                        return;
                    }
                    MinecraftServer server = serverMod.getServer();
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                }

                @Override
                public void publishBan(String playerName, String reason, String mods) {
                    HandShakerServerMod serverMod = HandShakerServerMod.getInstance();
                    if (serverMod != null) {
                        serverMod.publishWebhookBan(playerName, reason, mods);
                    }
                }

                @Override
                public void publishKick(String playerName, String reason, String mods) {
                    HandShakerServerMod serverMod = HandShakerServerMod.getInstance();
                    if (serverMod != null) {
                        serverMod.publishWebhookKick(playerName, reason, mods);
                    }
                }

                @Override
                public boolean hasBypassPermission() {
                    return false;
                }
            }
        );
    }

    private Map<String, String> collectKnownHashes() {
        if (!hashMods) {
            return Collections.emptyMap();
        }

        var serverMod = HandShakerServerMod.getInstance();
        if (serverMod == null || serverMod.getPlayerHistoryDb() == null) {
            return Collections.emptyMap();
        }

        PlayerHistoryDatabase db = serverMod.getPlayerHistoryDb();
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
}
