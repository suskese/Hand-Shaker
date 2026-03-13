package me.mklv.handshaker.fabric.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import me.mklv.handlib.fabric.PermissionsAdapter;
import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.CommonPlayerCheckEngine;
import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.configs.ConfigRuntime.ModConfigStore;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigLoadOptions;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.ModConfig;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.protocols.CollectKnownHashes;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import me.mklv.handshaker.common.utils.ModCache;

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

        ConfigLoadOptions options = new ConfigLoadOptions(true, false, true, "none", false);
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
        return super.isIgnored(modId);
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

    public void checkPlayer(ServerPlayer player, ClientInfo info) {
        checkPlayer(player, info, true, false); // Execute actions by default, not a timeout check
    }
    
    public void checkPlayer(ServerPlayer player, ClientInfo info, boolean executeActions) {
        checkPlayer(player, info, executeActions, false); // Not a timeout check
    }
    
    public void checkPlayer(ServerPlayer player, ClientInfo info, boolean executeActions, boolean isTimeoutCheck) {
        CommonPlayerCheckEngine.checkPlayer(
            this,
            player.getUUID(),
            player.getName().getString(),
            info,
            executeActions,
            isTimeoutCheck,
            collectKnownHashes(),
            new CommonPlayerCheckEngine.Bridge() {
                @Override
                public void info(String message) {
                    HandShakerServer.LOGGER.info(message);
                }

                @Override
                public void warn(String message) {
                    HandShakerServer.LOGGER.warn(message);
                }

                @Override
                public void disconnect(String message) {
                    player.connection.disconnect(Component.literal(message));
                }

                @Override
                public void executeServerCommand(String command) {
                    HandShakerServer serverInstance = HandShakerServer.getInstance();
                    if (serverInstance == null || serverInstance.getServer() == null) {
                        HandShakerServer.LOGGER.warn("Server instance is null, cannot execute action command");
                        return;
                    }
                    MinecraftServer server = serverInstance.getServer();
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
                }

                @Override
                public void publishBan(String playerName, String reason, String mods) {
                    HandShakerServer serverInstance = HandShakerServer.getInstance();
                    if (serverInstance != null) {
                        serverInstance.publishWebhookBan(playerName, reason, mods);
                    }
                }

                @Override
                public void publishKick(String playerName, String reason, String mods) {
                    HandShakerServer serverInstance = HandShakerServer.getInstance();
                    if (serverInstance != null) {
                        serverInstance.publishWebhookKick(playerName, reason, mods);
                    }
                }

                @Override
                public boolean hasBypassPermission() {
                    return PermissionsAdapter.checkPermission(player, "handshaker.bypass");
                }
            }
        );
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

    public void playerLeft(ServerPlayer player) {
    }

}
