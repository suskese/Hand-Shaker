package me.mklv.handshaker.paper;

import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.configs.ConfigRuntime.MessagePlaceholderExpander;
import me.mklv.handshaker.common.configs.ConfigTypes.ActionDefinition;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigLoadOptions;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.Behavior;
import me.mklv.handshaker.common.configs.ModChecks.ModCheckEvaluator;
import me.mklv.handshaker.common.configs.ModChecks.ModCheckInput;
import me.mklv.handshaker.common.configs.ModChecks.ModCheckResult;
import org.bukkit.entity.Player;

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

        ConfigFileBootstrap.Logger bootstrapLogger = new ConfigFileBootstrap.Logger() {
            @Override
            public void info(String message) {
                plugin.getLogger().info(message);
            }

            @Override
            public void warn(String message) {
                plugin.getLogger().warning(message);
            }

            @Override
            public void error(String message, Throwable error) {
                plugin.getLogger().severe(message + ": " + error.getMessage());
            }
        };

        ConfigLoadOptions options = new ConfigLoadOptions(true, false, true, "none", false);
        loadCommon(dataFolder.toPath(), plugin.getClass(), bootstrapLogger, options);

        // Ensure reload applies debug toggle immediately
        HandShakerPlugin.DEBUG = isDebug();
    }
    public ActionDefinition getActionOrDefault(String actionName, ActionDefinition defaultAction) {
        if (actionName == null) return defaultAction;
        ActionDefinition action = actionsMap.get(actionName.toLowerCase(Locale.ROOT));
        return action != null ? action : defaultAction;
    }

    public String checkPlayer(Player player, Set<String> clientMods) {
        if (player.hasPermission("handshaker.bypass")) {
            return null;
        }
        
        boolean hasMod = !clientMods.isEmpty();
        if (behavior == Behavior.VANILLA && !hasMod) {
            return null;
        }
        
        Set<String> missingRequired = new HashSet<>();
        Set<String> blacklistedFound = new HashSet<>();

        for (String modId : clientMods) {
            modId = modId.toLowerCase(Locale.ROOT);
            
            if (requiredModsActive.contains(modId) && !clientMods.contains(modId)) {
                missingRequired.add(modId);
            }
            
            if (blacklistedModsActive.contains(modId)) {
                blacklistedFound.add(modId);
            }
        }

        for (String modId : requiredModsActive) {
            if (!clientMods.contains(modId)) {
                missingRequired.add(modId);
            }
        }

        if (!missingRequired.isEmpty()) {
            return missingWhitelistModMessage.replace("{mod}", String.join(", ", missingRequired));
        }
        if (!blacklistedFound.isEmpty()) {
            return kickMessage.replace("{mod}", String.join(", ", blacklistedFound));
        }

        return null;
    }

    public ModCheckResult checkPlayerWithAction(Player player, Set<String> clientMods) {
        if (player.hasPermission("handshaker.bypass")) {
            return null;
        }
        
        if (HandShakerPlugin.DEBUG) {
            plugin.getLogger().fine("[DEBUG] Checking player " + player.getName() + " - Client mods: " + clientMods);
        }
        
        boolean hasMod = !clientMods.isEmpty();
        if (behavior == Behavior.VANILLA && !hasMod) {
            return null;
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
        ModCheckResult result = ModCheckEvaluator.evaluate(input, clientMods);
        if (!result.isViolation() && !result.hasAllowedActions()) {
            return null;
        }

        if (HandShakerPlugin.DEBUG && result.hasAllowedActions()) {
            String modList = String.join(", ", result.getMods());
            plugin.getLogger().info("[DEBUG] Allowed mod found: " + modList + ", action: " + result.getActionName());
        }

        return result;
    }

    public String replacePlaceholders(String command, Player player, Set<String> mods) {
        String modList = String.join(", ", mods);
        return MessagePlaceholderExpander.expand(command, player.getName(), modList, customMessages);
    }

    public void save() {
        ConfigFileBootstrap.Logger saveLogger = new ConfigFileBootstrap.Logger() {
            @Override
            public void info(String message) {
                plugin.getLogger().info(message);
            }

            @Override
            public void warn(String message) {
                plugin.getLogger().warning(message);
            }

            @Override
            public void error(String message, Throwable error) {
                plugin.getLogger().severe(message + ": " + error.getMessage());
            }
        };

        saveCommon(plugin.getDataFolder().toPath(), saveLogger);
    }

    private Map<String, String> collectKnownHashes() {
        if (!hashMods) {
            return Collections.emptyMap();
        }

        me.mklv.handshaker.paper.utils.PlayerHistoryDatabase db = plugin.getPlayerHistoryDb();
        if (db == null) {
            return Collections.emptyMap();
        }

        Set<String> ruleKeys = new LinkedHashSet<>();
        ruleKeys.addAll(requiredModsActive);
        ruleKeys.addAll(blacklistedModsActive);
        ruleKeys.addAll(whitelistedModsActive);
        ruleKeys.addAll(optionalModsActive);
        return db.getRegisteredHashes(ruleKeys, modVersioning);
    }
    
}
