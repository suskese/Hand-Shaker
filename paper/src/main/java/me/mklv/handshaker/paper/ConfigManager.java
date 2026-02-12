package me.mklv.handshaker.paper;

import me.mklv.handshaker.common.configs.ActionDefinition;
import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigState;
import me.mklv.handshaker.common.configs.ConfigLoadOptions;
import me.mklv.handshaker.common.configs.ConfigLoadResult;
import me.mklv.handshaker.common.configs.ConfigLoader;
import me.mklv.handshaker.common.configs.ConfigSnapshotBuilder;
import me.mklv.handshaker.common.configs.ConfigWriter;
import me.mklv.handshaker.common.configs.MessagePlaceholderExpander;
import me.mklv.handshaker.common.configs.ModConfigStore;
import me.mklv.handshaker.common.configs.ModCheckEvaluator;
import me.mklv.handshaker.common.configs.ModCheckInput;
import me.mklv.handshaker.common.configs.ModCheckResult;
import me.mklv.handshaker.common.configs.ConfigState.Behavior;
import me.mklv.handshaker.common.configs.ConfigState.IntegrityMode;
import me.mklv.handshaker.common.configs.ConfigState.ModConfig;
import org.bukkit.entity.Player;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;

public class ConfigManager {
    public static final String MODE_REQUIRED = ConfigState.MODE_REQUIRED;
    public static final String MODE_BLACKLISTED = ConfigState.MODE_BLACKLISTED;
    public static final String MODE_ALLOWED = ConfigState.MODE_ALLOWED;
    public static final String MODE_WHITELISTED = ConfigState.MODE_WHITELISTED;
    
    private final HandShakerPlugin plugin;

    public enum ModType {
        REQUIRED(ConfigState.MODE_REQUIRED),
        BLACKLISTED(ConfigState.MODE_BLACKLISTED),
        ALLOWED(ConfigState.MODE_ALLOWED),
        WHITELISTED(ConfigState.MODE_WHITELISTED);
        
        private final String modeName;
        
        ModType(String modeName) {
            this.modeName = modeName;
        }
        
        public String getModeName() { return modeName; }
        
        public static ModType fromString(String str) {
            if (str == null) return ALLOWED;
            return switch (str.toLowerCase(Locale.ROOT)) {
                case "required" -> REQUIRED;
                case "blacklisted" -> BLACKLISTED;
                case "whitelisted" -> WHITELISTED;
                default -> ALLOWED;
            };
        }
    }

    private Behavior behavior = Behavior.STRICT;
    private IntegrityMode integrityMode = IntegrityMode.SIGNED;
    private String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
    private String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
    private String invalidSignatureKickMessage = "Invalid client signature. Please use the official HandShaker client mod.";
    private boolean allowBedrockPlayers = false;
    private boolean playerdbEnabled = false;
    
    private final Map<String, String> customMessages = new LinkedHashMap<>();
    
    private boolean modsRequiredEnabled = true;
    private boolean modsBlacklistedEnabled = true;
    private boolean modsWhitelistedEnabled = true;
    
    private final Map<String, ModConfig> modConfigMap = new LinkedHashMap<>();
    private boolean whitelist = false;
    private final Set<String> ignoredMods = new HashSet<>();
    private final Set<String> whitelistedModsActive = new HashSet<>();
    private final Set<String> blacklistedModsActive = new HashSet<>();
    private final Set<String> requiredModsActive = new HashSet<>();
    private final Map<String, ActionDefinition> actionsMap = new LinkedHashMap<>();

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
        ConfigLoadResult result = ConfigLoader.load(dataFolder.toPath(), plugin.getClass(), bootstrapLogger, options);
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
        modsRequiredEnabled = result.areModsRequiredEnabled();
        modsBlacklistedEnabled = result.areModsBlacklistedEnabled();
        modsWhitelistedEnabled = result.areModsWhitelistedEnabled();
        whitelist = result.isWhitelist();

        customMessages.clear();
        customMessages.putAll(result.getMessages());

        modConfigMap.clear();
        modConfigMap.putAll(result.getModConfigMap());
        ignoredMods.clear();
        ignoredMods.addAll(result.getIgnoredMods());
        whitelistedModsActive.clear();
        whitelistedModsActive.addAll(result.getWhitelistedModsActive());
        blacklistedModsActive.clear();
        blacklistedModsActive.addAll(result.getBlacklistedModsActive());
        requiredModsActive.clear();
        requiredModsActive.addAll(result.getRequiredModsActive());
        actionsMap.clear();
        actionsMap.putAll(result.getActionsMap());
    }


    private void loadModsYamlFiles() {
        modConfigMap.clear();
        ignoredMods.clear();
        whitelistedModsActive.clear();
        blacklistedModsActive.clear();
        requiredModsActive.clear();

        File dataFolder = plugin.getDataFolder();
        Yaml yaml = new Yaml();

        File ignoredFile = new File(dataFolder, "mods-ignored.yml");
        if (ignoredFile.exists()) {
            try (FileReader reader = new FileReader(ignoredFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("ignored")) {
                    @SuppressWarnings("unchecked")
                    List<String> ignoredList = (List<String>) data.get("ignored");
                    if (ignoredList != null) {
                        for (String mod : ignoredList) {
                            ignoredMods.add(mod.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load mods-ignored.yml: " + e.getMessage());
            }
        }
        
        File requiredFile = new File(dataFolder, "mods-required.yml");
        if (requiredFile.exists()) {
            try (FileReader reader = new FileReader(requiredFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("required")) {
                    Object requiredObj = data.get("required");
                    if (requiredObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> requiredMap = (Map<String, Object>) requiredObj;
                        for (Map.Entry<String, Object> entry : requiredMap.entrySet()) {
                            String modId = entry.getKey().toLowerCase(Locale.ROOT);
                            String action = entry.getValue() != null ? entry.getValue().toString() : "kick";
                            requiredModsActive.add(modId);
                            modConfigMap.put(modId, new ModConfig("required", action, null));
                        }
                    } else {
                        plugin.getLogger().warning("Invalid structure in mods-required.yml: 'required' must be a map (modname: action), not a list");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load mods-required.yml: " + e.getMessage());
            }
        }

        File blacklistedFile = new File(dataFolder, "mods-blacklisted.yml");
        if (blacklistedFile.exists()) {
            try (FileReader reader = new FileReader(blacklistedFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("blacklisted")) {
                    Object blacklistedObj = data.get("blacklisted");
                    if (blacklistedObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> blacklistedMap = (Map<String, Object>) blacklistedObj;
                        for (Map.Entry<String, Object> entry : blacklistedMap.entrySet()) {
                            String modId = entry.getKey().toLowerCase(Locale.ROOT);
                            String action = entry.getValue() != null ? entry.getValue().toString() : "kick";
                            blacklistedModsActive.add(modId);
                            modConfigMap.put(modId, new ModConfig("blacklisted", action, null));
                        }
                    } else {
                        plugin.getLogger().warning("Invalid structure in mods-blacklisted.yml: 'blacklisted' must be a map (modname: action), not a list");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load mods-blacklisted.yml: " + e.getMessage());
            }
        }

        File whitelistedFile = new File(dataFolder, "mods-whitelisted.yml");
        if (modsWhitelistedEnabled && whitelistedFile.exists()) {
            try (FileReader reader = new FileReader(whitelistedFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("whitelisted")) {
                    Object whitelistedObj = data.get("whitelisted");
                    if (whitelistedObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> whitelistedMap = (Map<String, Object>) whitelistedObj;
                        for (Map.Entry<String, Object> entry : whitelistedMap.entrySet()) {
                            String modId = entry.getKey().toLowerCase(Locale.ROOT);
                            String action = entry.getValue() != null ? entry.getValue().toString() : "none";
                            whitelistedModsActive.add(modId);
                            modConfigMap.put(modId, new ModConfig("allowed", action, null));
                        }
                    } else {
                        plugin.getLogger().warning("Invalid structure in mods-whitelisted.yml: 'whitelisted' must be a map (modname: action), not a list");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load mods-whitelisted.yml: " + e.getMessage());
            }
        }
        
        if (modsWhitelistedEnabled && !whitelistedFile.exists()) {
            try (FileWriter writer = new FileWriter(whitelistedFile)) {
                writer.write("# Whitelisted mods which are allowed but not required,\n");
                writer.write("# but if in config.yml whitelist: true, only these mods are allowed\n");
                writer.write("# Format: modname: action (where action is from mods-actions.yml or default 'none')\n\n");
                writer.write("whitelisted:\n");
                plugin.getLogger().info("✓ Created mods-whitelisted.yml file (whitelisted mode enabled)");
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create mods-whitelisted.yml: " + e.getMessage());
            }
        }
    }


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
    public Set<String> getBlacklistedMods() { return Collections.unmodifiableSet(blacklistedModsActive); }
    public Set<String> getRequiredMods() { return Collections.unmodifiableSet(requiredModsActive); }
    public boolean areModsRequiredEnabled() { return modsRequiredEnabled; }
    public boolean areModsBlacklistedEnabled() { return modsBlacklistedEnabled; }
    public boolean areModsWhitelistedEnabled() { return modsWhitelistedEnabled; }
    public boolean isPlayerdbEnabled() { return playerdbEnabled; }
    public ActionDefinition getAction(String actionName) { 
        if (actionName == null) return null;
        return actionsMap.get(actionName.toLowerCase(Locale.ROOT));
    }
    public ActionDefinition getActionOrDefault(String actionName, ActionDefinition defaultAction) {
        if (actionName == null) return defaultAction;
        ActionDefinition action = actionsMap.get(actionName.toLowerCase(Locale.ROOT));
        return action != null ? action : defaultAction;
    }
    public Set<String> getAvailableActions() {
        return Collections.unmodifiableSet(actionsMap.keySet());
    }

    public void setBehavior(String value) {
        this.behavior = value.equalsIgnoreCase("STRICT") ? Behavior.STRICT : Behavior.VANILLA;
    }

    public void setIntegrityMode(String value) {
        this.integrityMode = value.equalsIgnoreCase("SIGNED") ? IntegrityMode.SIGNED : IntegrityMode.DEV;
    }

    public void setDefaultMode(String value) {
        this.whitelist = value.equalsIgnoreCase("BLACKLISTED");
    }

    public void setWhitelist(boolean value) {
        this.whitelist = value;
    }

    public boolean toggleWhitelistedModsActive() {
        modsWhitelistedEnabled = !modsWhitelistedEnabled;
        loadModsYamlFiles();
        save();
        return modsWhitelistedEnabled;
    }

    public boolean toggleBlacklistedModsActive() {
        modsBlacklistedEnabled = !modsBlacklistedEnabled;
        loadModsYamlFiles();
        save();
        return modsBlacklistedEnabled;
    }

    public boolean toggleRequiredModsActive() {
        modsRequiredEnabled = !modsRequiredEnabled;
        loadModsYamlFiles();
        save();
        return modsRequiredEnabled;
    }

    public void setKickMessage(String message) { this.kickMessage = message; }
    public void setNoHandshakeKickMessage(String message) { this.noHandshakeKickMessage = message; }
    public void setMissingWhitelistModMessage(String message) { this.missingWhitelistModMessage = message; }
    public void setInvalidSignatureKickMessage(String message) { this.invalidSignatureKickMessage = message; }
    public void setAllowBedrockPlayers(boolean allow) { this.allowBedrockPlayers = allow; }

    public void setPlayerdbEnabled(boolean enabled) {
        this.playerdbEnabled = enabled;
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
        String defaultModeStr = whitelist ? MODE_BLACKLISTED : MODE_ALLOWED;
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
            "none",
            "kick"
        );
        save();
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
            ignoredMods,
            whitelistedModsActive,
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
            customMessages,
            modConfigMap,
            ignoredMods,
            whitelistedModsActive,
            blacklistedModsActive,
            requiredModsActive,
            actionsMap
        );

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

        ConfigWriter.writeAll(plugin.getDataFolder().toPath(), saveLogger, snapshot);
    }
    
}
