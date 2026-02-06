package me.mklv.handshaker.paper.configs;

import me.mklv.handshaker.paper.HandShakerPlugin;
import me.mklv.handshaker.paper.utils.PlayerModStatus;
import org.bukkit.entity.Player;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class ConfigManager {
    public static final String MODE_REQUIRED = "required";
    public static final String MODE_BLACKLISTED = "blacklisted";
    public static final String MODE_ALLOWED = "allowed";
    public static final String MODE_WHITELISTED = "whitelisted";
    
    private final HandShakerPlugin plugin;
    private File configYmlFile;

    public enum Behavior { STRICT, VANILLA }
    public enum IntegrityMode { SIGNED, DEV }
    
    public enum ModType {
        REQUIRED(MODE_REQUIRED),
        BLACKLISTED(MODE_BLACKLISTED),
        ALLOWED(MODE_ALLOWED),
        WHITELISTED(MODE_WHITELISTED);
        
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
    
    public enum Action {
        KICK, BAN;
        
        public static Action fromString(String str) {
            if (str == null) return KICK;
            return switch (str.toUpperCase(Locale.ROOT)) {
                case "BAN" -> BAN;
                default -> KICK;
            };
        }
    }

    public static class ModConfig {
        private String mode;
        private String action;
        private String warnMessage;

        public ModConfig(String mode, String action, String warnMessage) {
            this.mode = mode != null ? mode : MODE_ALLOWED;
            if (action != null) {
                this.action = action;
            } else {
                String modeLower = this.mode.toLowerCase(Locale.ROOT);
                this.action = MODE_ALLOWED.equals(modeLower) ? "none" : "kick";
            }
            this.warnMessage = warnMessage;
        }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getActionName() { return action; }
        public Action getAction() { return Action.fromString(action); }
        public void setAction(String action) { this.action = action; }
        public String getWarnMessage() { return warnMessage; }
        public void setWarnMessage(String warnMessage) { this.warnMessage = warnMessage; }
        
        public boolean isRequired() { return MODE_REQUIRED.equalsIgnoreCase(mode); }
        public boolean isBlacklisted() { return MODE_BLACKLISTED.equalsIgnoreCase(mode); }
        public boolean isAllowed() { return MODE_ALLOWED.equalsIgnoreCase(mode); }
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

        configYmlFile = new File(dataFolder, "config.yml");

        createDefaultFilesIfNotExist(dataFolder);

        loadConfigYml();
        loadModsYamlFiles();
        loadActionsYamlFile();
    }

    private void createDefaultFilesIfNotExist(File dataFolder) {
        if (!configYmlFile.exists()) {
            try {
                Files.copy(
                    Objects.requireNonNull(plugin.getClass().getResourceAsStream("/configs/config.yml")),
                    configYmlFile.toPath()
                );
                plugin.getLogger().info("Created default config.yml from plugin resources");
            } catch (IOException | NullPointerException e) {
                plugin.getLogger().severe("Could not create config.yml from plugin resources: " + e.getMessage());
                plugin.getLogger().severe("Make sure config.yml is included in the plugin JAR");
                throw new RuntimeException("Failed to load config.yml", e);
            }
        }

        String[] modsFiles = {"mods-required.yml", "mods-blacklisted.yml", "mods-whitelisted.yml", "mods-ignored.yml", "mods-actions.yml"};
        for (String filename : modsFiles) {
            File file = new File(dataFolder, filename);
            if (!file.exists()) {
                try {
                    Files.copy(
                        Objects.requireNonNull(plugin.getClass().getResourceAsStream("/configs/" + filename)),
                        file.toPath()
                    );
                    plugin.getLogger().info("Created default " + filename + " from plugin resources");
                } catch (IOException | NullPointerException e) {
                    plugin.getLogger().warning("Could not create " + filename + " from plugin resources: " + e.getMessage());
                    plugin.getLogger().warning("This file will be created with defaults on next save");
                }
            }
        }
    }

    private void loadConfigYml() {
        try (FileReader reader = new FileReader(configYmlFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);

            if (data != null) {
                if (data.containsKey("behavior")) {
                    String behaviorStr = data.get("behavior").toString().toLowerCase();
                    behavior = behaviorStr.startsWith("strict") ? Behavior.STRICT : Behavior.VANILLA;
                }

                if (data.containsKey("integrity-mode")) {
                    String integrityStr = data.get("integrity-mode").toString().toLowerCase();
                    integrityMode = integrityStr.equals("dev") ? IntegrityMode.DEV : IntegrityMode.SIGNED;
                }

                if (data.containsKey("whitelist")) {
                    whitelist = Boolean.parseBoolean(data.get("whitelist").toString());
                }

                if (data.containsKey("allow-bedrock-players")) {
                    allowBedrockPlayers = Boolean.parseBoolean(data.get("allow-bedrock-players").toString());
                }

                if (data.containsKey("playerdb-enabled")) {
                    playerdbEnabled = Boolean.parseBoolean(data.get("playerdb-enabled").toString());
                }

                if (data.containsKey("mods-required-enabled")) {
                    modsRequiredEnabled = Boolean.parseBoolean(data.get("mods-required-enabled").toString());
                }
                if (data.containsKey("mods-blacklisted-enabled")) {
                    modsBlacklistedEnabled = Boolean.parseBoolean(data.get("mods-blacklisted-enabled").toString());
                }
                if (data.containsKey("mods-whitelisted-enabled")) {
                    modsWhitelistedEnabled = Boolean.parseBoolean(data.get("mods-whitelisted-enabled").toString());
                }

                if (data.containsKey("messages")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messages = (Map<String, Object>) data.get("messages");
                    if (messages != null) {
                        customMessages.clear();
                        if (messages.containsKey("kick")) {
                            kickMessage = messages.get("kick").toString();
                            customMessages.put("kick", kickMessage);
                        }
                        if (messages.containsKey("no-handshake")) {
                            noHandshakeKickMessage = messages.get("no-handshake").toString();
                            customMessages.put("no-handshake", noHandshakeKickMessage);
                        }
                        if (messages.containsKey("missing-whitelist")) {
                            missingWhitelistModMessage = messages.get("missing-whitelist").toString();
                            customMessages.put("missing-whitelist", missingWhitelistModMessage);
                        }
                        if (messages.containsKey("invalid-signature")) {
                            invalidSignatureKickMessage = messages.get("invalid-signature").toString();
                            customMessages.put("invalid-signature", invalidSignatureKickMessage);
                        }
                        for (Map.Entry<String, Object> entry : messages.entrySet()) {
                            if (!customMessages.containsKey(entry.getKey())) {
                                customMessages.put(entry.getKey(), entry.getValue().toString());
                            }
                        }
                        plugin.getLogger().info("✓ Loaded custom messages from config.yml");
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load config.yml: " + e.getMessage());
        }
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

    private void loadActionsYamlFile() {
        File dataFolder = plugin.getDataFolder();
        File actionsFile = new File(dataFolder, "mods-actions.yml");
        actionsMap.clear();

        if (actionsFile.exists()) {
            try (FileReader reader = new FileReader(actionsFile)) {
                Yaml yaml = new Yaml();
                Map<String, Object> data = yaml.load(reader);
                
                if (data != null && data.containsKey("actions")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> actionsObj = (Map<String, Object>) data.get("actions");
                    
                    if (actionsObj != null) {
                        for (Map.Entry<String, Object> entry : actionsObj.entrySet()) {
                            String actionName = entry.getKey().toLowerCase(Locale.ROOT);
                            Object actionValue = entry.getValue();
                            
                            if (actionValue instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> actionMap = (Map<String, Object>) actionValue;
                                ActionDefinition action;
                                
                                boolean shouldLog = false;
                                if (actionMap.containsKey("log")) {
                                    Object logObj = actionMap.get("log");
                                    shouldLog = logObj instanceof Boolean ? (Boolean) logObj : Boolean.parseBoolean(logObj.toString());
                                }
                                
                                List<String> commands = new ArrayList<>();
                                if (actionMap.containsKey("commands")) {
                                    Object commandsObj = actionMap.get("commands");
                                    if (commandsObj instanceof List) {
                                        @SuppressWarnings("unchecked")
                                        List<String> cmdList = (List<String>) commandsObj;
                                        commands.addAll(cmdList);
                                    }
                                }
                                
                                action = new ActionDefinition(actionName, commands, shouldLog);
                                
                                if (!action.isEmpty() || shouldLog) {
                                    actionsMap.put(actionName, action);
                                }
                            }
                        }
                    }
                }
                
                if (!actionsMap.isEmpty()) {
                    plugin.getLogger().info("Loaded " + actionsMap.size() + " action(s) from mods-actions.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to load mods-actions.yml: " + e.getMessage());
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
        modId = modId.toLowerCase(Locale.ROOT);
        ModConfig existing = modConfigMap.get(modId);
        
        if (existing != null) {
            if (mode != null) existing.setMode(mode);
            if (action != null) existing.setAction(action);
            if (warnMessage != null) existing.setWarnMessage(warnMessage);
        } else {
            modConfigMap.put(modId, new ModConfig(mode, action, warnMessage));
        }
        
        if (mode != null) {
            requiredModsActive.remove(modId);
            blacklistedModsActive.remove(modId);
            whitelistedModsActive.remove(modId);
            
            String modeLower = mode.toLowerCase();
            if (MODE_REQUIRED.equals(modeLower)) {
                requiredModsActive.add(modId);
            } else if (MODE_BLACKLISTED.equals(modeLower)) {
                blacklistedModsActive.add(modId);
            } else if (MODE_ALLOWED.equals(modeLower) || MODE_WHITELISTED.equals(modeLower)) {
                whitelistedModsActive.add(modId);
            }
        }
        
        save();
        return true;
    }

    public boolean removeModConfig(String modId) {
        modId = modId.toLowerCase(Locale.ROOT);
        boolean removed = modConfigMap.remove(modId) != null;
        if (removed) {
            requiredModsActive.remove(modId);
            blacklistedModsActive.remove(modId);
            whitelistedModsActive.remove(modId);
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
        String modeLower = mode.toLowerCase();
        for (String mod : mods) {
            String modId = mod.toLowerCase(Locale.ROOT);
            modConfigMap.put(modId, new ModConfig(mode, action, warnMessage));
            
            requiredModsActive.remove(modId);
            blacklistedModsActive.remove(modId);
            whitelistedModsActive.remove(modId);
            
            if (MODE_REQUIRED.equals(modeLower)) {
                requiredModsActive.add(modId);
            } else if (MODE_BLACKLISTED.equals(modeLower)) {
                blacklistedModsActive.add(modId);
            } else if (MODE_ALLOWED.equals(modeLower) || MODE_WHITELISTED.equals(modeLower)) {
                whitelistedModsActive.add(modId);
            }
        }
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

    public PlayerModStatus checkPlayerWithAction(Player player, Set<String> clientMods) {
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
        
        Set<String> missingRequired = new HashSet<>();
        Set<String> blacklistedFound = new HashSet<>();

        for (String modId : clientMods) {
            String modIdLower = modId.toLowerCase(Locale.ROOT);
            
            if (requiredModsActive.contains(modIdLower) && !clientMods.contains(modIdLower)) {
                missingRequired.add(modIdLower);
            }
            
            if (blacklistedModsActive.contains(modIdLower)) {
                blacklistedFound.add(modIdLower);
            }
        }

        for (String modId : requiredModsActive) {
            if (!clientMods.contains(modId)) {
                missingRequired.add(modId);
            }
        }

        if (!blacklistedFound.isEmpty()) {
            String modList = String.join(", ", blacklistedFound);
            ModConfig cfg = modConfigMap.get(blacklistedFound.iterator().next().toLowerCase(Locale.ROOT));
            String actionName = cfg != null ? cfg.getActionName() : "kick";
            return new PlayerModStatus(kickMessage.replace("{mod}", modList), actionName, blacklistedFound, false, true);
        }

        if (!missingRequired.isEmpty()) {
            String modList = String.join(", ", missingRequired);
            String actionName = "kick";
            if (!missingRequired.isEmpty()) {
                ModConfig cfg = modConfigMap.get(missingRequired.iterator().next().toLowerCase(Locale.ROOT));
                if (cfg != null && cfg.getActionName() != null) {
                    actionName = cfg.getActionName();
                }
            }
            return new PlayerModStatus(missingWhitelistModMessage.replace("{mod}", modList), actionName, missingRequired, true, false);
        }

        if (whitelist) {
            Set<String> nonWhitelistedMods = new HashSet<>();
            for (String modId : clientMods) {
                String modIdLower = modId.toLowerCase(Locale.ROOT);
                if (!ignoredMods.contains(modIdLower) && !whitelistedModsActive.contains(modIdLower)) {
                    nonWhitelistedMods.add(modIdLower);
                }
            }
            if (!nonWhitelistedMods.isEmpty()) {
                String modList = String.join(", ", nonWhitelistedMods);
                ModConfig cfg = modConfigMap.get(nonWhitelistedMods.iterator().next().toLowerCase(Locale.ROOT));
                String actionName = cfg != null ? cfg.getActionName() : "kick";
                return new PlayerModStatus(kickMessage.replace("{mod}", modList), actionName, nonWhitelistedMods, false, false);
            }
        }
        
        if (modsWhitelistedEnabled) {
            Set<String> allowedModsWithAction = new HashSet<>();
            String firstAction = "none";
        
            for (String modId : clientMods) {
                String modIdLower = modId.toLowerCase(Locale.ROOT);
                ModConfig cfg = modConfigMap.get(modIdLower);
                if (cfg != null && cfg.isAllowed()) {
                    String action = cfg.getActionName();
                    if (action != null && !action.equals("none")) {
                        allowedModsWithAction.add(modIdLower);
                        if (firstAction.equals("none")) {
                            firstAction = action;
                        }
                    }
                }
            }
        
            if (!allowedModsWithAction.isEmpty()) {
                String modList = String.join(", ", allowedModsWithAction);
                if (HandShakerPlugin.DEBUG) {
                    plugin.getLogger().info("[DEBUG] Allowed mod found: " + modList + ", action: " + firstAction);
                }
                return new PlayerModStatus(null, firstAction, allowedModsWithAction, false, false);
            }
        }
        return null;
    }

    public String replacePlaceholders(String command, Player player, Set<String> mods) {
        String modList = String.join(", ", mods);
        String result = command;
        
        Map<String, String> replacements = new HashMap<>();
        replacements.put("{player}", player.getName());
        replacements.put("{mod}", modList);
        
        for (Map.Entry<String, String> entry : customMessages.entrySet()) {
            String messageValue = entry.getValue()
                .replace("{player}", player.getName())
                .replace("{mod}", modList);
            replacements.put("{messages." + entry.getKey() + "}", messageValue);
        }
        
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        
        return result;
    }

    public void save() {
        String defaultConfig = loadDefaultConfigFromJar();
        
        if (defaultConfig == null) {
            plugin.getLogger().severe("Could not load default config from JAR!");
            return;
        }
        
        String yaml = defaultConfig
            .replaceAll("behavior:\\s*\\w+", "behavior: " + behavior.toString().toLowerCase())
            .replaceAll("integrity-mode:\\s*\\w+", "integrity-mode: " + integrityMode.toString().toLowerCase())
            .replaceAll("whitelist:\\s*(?:true|false)", "whitelist: " + whitelist)
            .replaceAll("allow-bedrock-players:\\s*(?:true|false)", "allow-bedrock-players: " + allowBedrockPlayers)
            .replaceAll("playerdb-enabled:\\s*(?:true|false)", "playerdb-enabled: " + playerdbEnabled)
            .replaceAll("mods-required-enabled:\\s*(?:true|false)", "mods-required-enabled: " + modsRequiredEnabled)
            .replaceAll("mods-blacklisted-enabled:\\s*(?:true|false)", "mods-blacklisted-enabled: " + modsBlacklistedEnabled)
            .replaceAll("mods-whitelisted-enabled:\\s*(?:true|false)", "mods-whitelisted-enabled: " + modsWhitelistedEnabled);
        
        try (FileWriter writer = new FileWriter(configYmlFile)) {
            writer.write(yaml);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save config.yml!");
        }

        saveModsYamlFiles();
    }

    private String loadDefaultConfigFromJar() {
        try (java.io.InputStream is = plugin.getClass().getResourceAsStream("/configs/config.yml")) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load default config from JAR: " + e.getMessage());
            return null;
        }
    }

    private void saveModsYamlFiles() {
        File dataFolder = plugin.getDataFolder();

        if (!ignoredMods.isEmpty()) {
            File ignoredFile = new File(dataFolder, "mods-ignored.yml");
            try (FileWriter writer = new FileWriter(ignoredFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Mods which will be hidden from commands to show up\n\n");
                yaml.append("ignored:\n");
                for (String mod : ignoredMods) {
                    yaml.append("  - ").append(mod).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save mods-ignored.yml!");
            }
        }

        if (!requiredModsActive.isEmpty()) {
            File requiredFile = new File(dataFolder, "mods-required.yml");
            try (FileWriter writer = new FileWriter(requiredFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Required mods to join the server\n");
                yaml.append("# Format: modname: action (where action is from mods-actions.yml or default 'kick')\n\n");
                yaml.append("required:\n");
                for (String mod : requiredModsActive) {
                    ModConfig cfg = modConfigMap.get(mod);
                    String action = cfg != null ? cfg.getActionName() : "kick";
                    yaml.append("  ").append(mod).append(": ").append(action).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save mods-required.yml!");
            }
        }

        if (!blacklistedModsActive.isEmpty()) {
            File blacklistedFile = new File(dataFolder, "mods-blacklisted.yml");
            try (FileWriter writer = new FileWriter(blacklistedFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Blacklisted mods: modname: kick/ban\n# If a player has any of these mods, they will be kicked\n\n");
                yaml.append("blacklisted:\n");
                for (String mod : blacklistedModsActive) {
                    ModConfig cfg = modConfigMap.get(mod);
                    String action = cfg != null ? cfg.getAction().toString().toLowerCase() : "kick";
                    yaml.append("  ").append(mod).append(": ").append(action).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save mods-blacklisted.yml!");
            }
        }

        if (!whitelistedModsActive.isEmpty()) {
            File whitelistedFile = new File(dataFolder, "mods-whitelisted.yml");
            try (FileWriter writer = new FileWriter(whitelistedFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Whitelisted mods which are allowed but not required,\n");
                yaml.append("# but if in config.yml whitelist: true, only these mods are allowed\n");
                yaml.append("# Format: modname: action (where action is from mods-actions.yml or default 'none')\n\n");
                yaml.append("whitelisted:\n");
                for (String mod : whitelistedModsActive) {
                    ModConfig cfg = modConfigMap.get(mod);
                    String action = cfg != null ? cfg.getActionName() : "none";
                    yaml.append("  ").append(mod).append(": ").append(action).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save mods-whitelisted.yml!");
            }
        }
    }
}
