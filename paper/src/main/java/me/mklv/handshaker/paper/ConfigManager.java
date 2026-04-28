package me.mklv.handshaker.paper;

import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigLoadOptions;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.protocols.CollectKnownHashes;
import me.mklv.handshaker.common.utils.LoggerAdapter;
import me.mklv.handshaker.common.utils.ModCache;

import java.io.*;
import java.util.*;

public class ConfigManager extends CommonConfigManagerBase {
    private final HandShakerPlugin plugin;

    public ConfigManager(HandShakerPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File dataFolder = plugin.getDataFolder();
        dataFolder.mkdirs();

        ConfigFileBootstrap.Logger bootstrapLogger = LoggerAdapter.fromLoaderLogger(plugin.getLogger());

        ConfigLoadOptions options = new ConfigLoadOptions(true, false, true, "none", false);
        loadCommon(dataFolder.toPath(), plugin.getClass(), bootstrapLogger, options);
        ModCache.invalidate();

        // Ensure reload applies debug toggle immediately
        HandShakerPlugin.DEBUG = isDebug();
    }

    public void save() {
        ConfigFileBootstrap.Logger saveLogger = LoggerAdapter.fromLoaderLogger(plugin.getLogger());

        saveCommon(plugin.getDataFolder().toPath(), saveLogger);
    }

    private Map<String, String> collectKnownHashes() {
        PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
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

    public Map<String, String> collectKnownHashesForChecks() {
        return collectKnownHashes();
    }
    
}
