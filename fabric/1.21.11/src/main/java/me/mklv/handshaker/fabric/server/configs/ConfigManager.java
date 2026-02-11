package me.mklv.handshaker.fabric.server.configs;

import net.fabricmc.loader.api.FabricLoader;
import org.yaml.snakeyaml.Yaml;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import me.mklv.handshaker.common.configs.ActionDefinition;
import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
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
    private File configYmlFile;

    private Behavior behavior = Behavior.STRICT;
    private IntegrityMode integrityMode = IntegrityMode.SIGNED;
    private String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
    private String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
    private String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
    private boolean allowBedrockPlayers = false;
    private boolean playerdbEnabled = false; // Default: disabled for security
    
    // Mod list toggle states - persisted in config
    private boolean modsRequiredEnabled = true;
    private boolean modsBlacklistedEnabled = true;
    private boolean modsWhitelistedEnabled = false;
    
    private final Map<String, ModConfig> modConfigMap = new LinkedHashMap<>();
    private boolean whitelist = false;
    private final Set<String> ignoredMods = new HashSet<>();
    private final Set<String> whitelistedModsActive = new HashSet<>();
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

        configYmlFile = new File(configDir, "config.yml");

        // Check if v4 files exist, if not create them from defaults
        createDefaultFilesIfNotExist();

        loadConfigYml();
        loadModsYamlFiles();
        loadActionsYamlFile();
    }

    private void createDefaultFilesIfNotExist() {
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

        ConfigFileBootstrap.copyRequired(configDir.toPath(), "config.yml", ConfigManager.class, bootstrapLogger);

        String[] modsFiles = {"mods-required.yml", "mods-blacklisted.yml", "mods-whitelisted.yml", "mods-ignored.yml", "mods-actions.yml"};
        for (String filename : modsFiles) {
            ConfigFileBootstrap.copyOptional(configDir.toPath(), filename, ConfigManager.class, bootstrapLogger);
        }
    }

    private void loadConfigYml() {
        try (FileReader reader = new FileReader(configYmlFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);

            if (data != null) {
                // Load behavior
                if (data.containsKey("behavior")) {
                    String behaviorStr = data.get("behavior").toString().toLowerCase();
                    behavior = behaviorStr.startsWith("strict") ? Behavior.STRICT : Behavior.VANILLA;
                }

                // Load integrity mode
                if (data.containsKey("integrity-mode")) {
                    String integrityStr = data.get("integrity-mode").toString().toLowerCase();
                    integrityMode = integrityStr.equals("dev") ? IntegrityMode.DEV : IntegrityMode.SIGNED;
                    HandShakerServer.LOGGER.info("Loaded integrity-mode from config: {} (raw: {})", integrityMode, integrityStr);
                } else {
                    HandShakerServer.LOGGER.warn("No integrity-mode in config, defaulting to SIGNED");
                }

                // Load whitelist mode
                if (data.containsKey("whitelist")) {
                    whitelist = Boolean.parseBoolean(data.get("whitelist").toString());
                }

                // Load bedrock setting
                if (data.containsKey("allow-bedrock-players")) {
                    allowBedrockPlayers = Boolean.parseBoolean(data.get("allow-bedrock-players").toString());
                }

                // Load playerdb enabled setting
                if (data.containsKey("playerdb-enabled")) {
                    playerdbEnabled = Boolean.parseBoolean(data.get("playerdb-enabled").toString());
                }

                // Load mod list toggle states
                if (data.containsKey("mods-required-enabled")) {
                    modsRequiredEnabled = Boolean.parseBoolean(data.get("mods-required-enabled").toString());
                }
                if (data.containsKey("mods-blacklisted-enabled")) {
                    modsBlacklistedEnabled = Boolean.parseBoolean(data.get("mods-blacklisted-enabled").toString());
                }
                if (data.containsKey("mods-whitelisted-enabled")) {
                    modsWhitelistedEnabled = Boolean.parseBoolean(data.get("mods-whitelisted-enabled").toString());
                }

                // Load messages
                if (data.containsKey("messages")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messages = (Map<String, Object>) data.get("messages");
                    if (messages != null) {
                        messagesMap.clear();
                        messagesMap.putAll(messages.entrySet().stream().collect(
                            java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue() != null ? e.getValue().toString() : ""
                            )
                        ));
                        
                        if (messages.containsKey("kick")) {
                            kickMessage = messages.get("kick").toString();
                        }
                        if (messages.containsKey("no-handshake")) {
                            noHandshakeKickMessage = messages.get("no-handshake").toString();
                        }
                        if (messages.containsKey("missing-whitelist")) {
                            missingWhitelistModMessage = messages.get("missing-whitelist").toString();
                        }
                        if (messages.containsKey("invalid-signature")) {
                            invalidSignatureKickMessage = messages.get("invalid-signature").toString();
                        }
                    }
                }
            }
        } catch (IOException e) {
            HandShakerServer.LOGGER.warn("Failed to load config.yml: {}", e.getMessage());
        }
    }

    private void loadModsYamlFiles() {
        modConfigMap.clear();
        ignoredMods.clear();
        whitelistedModsActive.clear();
        blacklistedModsActive.clear();
        requiredModsActive.clear();

        Yaml yaml = new Yaml();

        // Load ignored mods
        File ignoredFile = new File(configDir, "mods-ignored.yml");
        if (ignoredFile.exists()) {
            try (FileReader reader = new FileReader(ignoredFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("ignored")) {
                    try {
                        @SuppressWarnings("unchecked")
                        List<String> ignoredList = (List<String>) data.get("ignored");
                        if (ignoredList != null) {
                            for (String mod : ignoredList) {
                                ignoredMods.add(mod.toLowerCase(Locale.ROOT));
                            }
                        }
                    } catch (ClassCastException e) {
                        HandShakerServer.LOGGER.warn("Invalid format in mods-ignored.yml, expected list format");
                    }
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.warn("Failed to load mods-ignored.yml: {}", e.getMessage());
            }
        }

        // Load required mods
        File requiredFile = new File(configDir, "mods-required.yml");
        if (requiredFile.exists()) {
            try (FileReader reader = new FileReader(requiredFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("required")) {
                    Object requiredObj = data.get("required");
                    if (requiredObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> requiredMap = (Map<String, Object>) requiredObj;
                        if (requiredMap != null) {
                            for (Map.Entry<String, Object> entry : requiredMap.entrySet()) {
                                String modId = entry.getKey().toLowerCase(Locale.ROOT);
                                String action = entry.getValue() != null ? entry.getValue().toString() : "kick";
                                requiredModsActive.add(modId);
                                modConfigMap.put(modId, new ModConfig("required", action, null));
                            }
                        }
                    } else if (requiredObj instanceof List) {
                        // Fallback for legacy list format
                        @SuppressWarnings("unchecked")
                        List<String> requiredList = (List<String>) requiredObj;
                        if (requiredList != null) {
                            for (String mod : requiredList) {
                                String modId = mod.toLowerCase(Locale.ROOT);
                                requiredModsActive.add(modId);
                                modConfigMap.put(modId, new ModConfig("required", "kick", null));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.warn("Failed to load mods-required.yml: {}", e.getMessage());
            }
        }

        // Load blacklisted mods
        File blacklistedFile = new File(configDir, "mods-blacklisted.yml");
        if (blacklistedFile.exists()) {
            try (FileReader reader = new FileReader(blacklistedFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("blacklisted")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> blacklistedMap = (Map<String, Object>) data.get("blacklisted");
                    if (blacklistedMap != null) {
                        for (Map.Entry<String, Object> entry : blacklistedMap.entrySet()) {
                            String modId = entry.getKey().toLowerCase(Locale.ROOT);
                            String action = entry.getValue() != null ? entry.getValue().toString() : "kick";
                            blacklistedModsActive.add(modId);
                            modConfigMap.put(modId, new ModConfig("blacklisted", action, null));
                        }
                    }
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.warn("Failed to load mods-blacklisted.yml: {}", e.getMessage());
            }
        }

        // Load whitelisted mods
        File whitelistedFile = new File(configDir, "mods-whitelisted.yml");
        if (whitelistedFile.exists()) {
            try (FileReader reader = new FileReader(whitelistedFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("whitelisted")) {
                    Object whitelistedObj = data.get("whitelisted");
                    if (whitelistedObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> whitelistedMap = (Map<String, Object>) whitelistedObj;
                        for (Map.Entry<String, Object> entry : whitelistedMap.entrySet()) {
                            String modId = entry.getKey().toLowerCase(Locale.ROOT);
                            String action = entry.getValue() != null ? entry.getValue().toString() : "kick";
                            whitelistedModsActive.add(modId);
                            modConfigMap.put(modId, new ModConfig("allowed", action, null));
                        }
                    } else if (whitelistedObj instanceof List) {
                        // Fallback for legacy list format
                        @SuppressWarnings("unchecked")
                        List<String> whitelistedList = (List<String>) whitelistedObj;
                        if (whitelistedList != null) {
                            for (String mod : whitelistedList) {
                                String modId = mod.toLowerCase(Locale.ROOT);
                                whitelistedModsActive.add(modId);
                                modConfigMap.put(modId, new ModConfig("allowed", "kick", null));
                            }
                        }
                    }
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.warn("Failed to load mods-whitelisted.yml: {}", e.getMessage());
            }
        }
    }

    private void loadActionsYamlFile() {
        File actionsFile = new File(configDir, "mods-actions.yml");
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
                                
                                // Check for log flag
                                boolean shouldLog = false;
                                if (actionMap.containsKey("log")) {
                                    Object logObj = actionMap.get("log");
                                    shouldLog = logObj instanceof Boolean ? (Boolean) logObj : Boolean.parseBoolean(logObj.toString());
                                }
                                
                                // Load commands
                                List<String> commands = new ArrayList<>();
                                if (actionMap.containsKey("commands")) {
                                    Object commandsObj = actionMap.get("commands");
                                    if (commandsObj instanceof List) {
                                        @SuppressWarnings("unchecked")
                                        List<String> cmdList = (List<String>) commandsObj;
                                        commands.addAll(cmdList);
                                    }
                                }
                                
                                // Create action with log flag
                                action = new ActionDefinition(actionName, commands, shouldLog);
                                
                                // Always store the action, even if empty (for reference)
                                actionsMap.put(actionName, action);
                                HandShakerServer.LOGGER.info("Loaded action '{}': {} commands, log={}", actionName, commands.size(), shouldLog);
                            }
                        }
                    }
                }
                
                if (!actionsMap.isEmpty()) {
                    HandShakerServer.LOGGER.info("Loaded {} action(s) from mods-actions.yml: {}", actionsMap.size(), actionsMap.keySet());
                } else {
                    HandShakerServer.LOGGER.warn("No actions found in mods-actions.yml or file is empty");
                }
            } catch (IOException e) {
                HandShakerServer.LOGGER.warn("Failed to load mods-actions.yml: {}", e.getMessage());
            }
        }
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
    public Set<String> getBlacklistedMods() { return Collections.unmodifiableSet(blacklistedModsActive); }
    public Set<String> getRequiredMods() { return Collections.unmodifiableSet(requiredModsActive); }
    public boolean isPlayerdbEnabled() { return playerdbEnabled; }
    public boolean areModsRequiredEnabled() { return modsRequiredEnabled; }
    public boolean areModsBlacklistedEnabled() { return modsBlacklistedEnabled; }
    public boolean areModsWhitelistedEnabled() { return modsWhitelistedEnabled; }
    public ActionDefinition getAction(String actionName) { 
        if (actionName == null) return null;
        return actionsMap.get(actionName.toLowerCase(Locale.ROOT));
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
                    @SuppressWarnings("unchecked")
                    List<String> whitelistedList = (List<String>) data.get("whitelisted");
                    if (whitelistedList != null) {
                        for (String mod : whitelistedList) {
                            whitelistedModsActive.add(mod.toLowerCase(Locale.ROOT));
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
                    @SuppressWarnings("unchecked")
                    Map<String, Object> blacklistedMap = (Map<String, Object>) data.get("blacklisted");
                    if (blacklistedMap != null) {
                        for (String mod : blacklistedMap.keySet()) {
                            blacklistedModsActive.add(mod.toLowerCase(Locale.ROOT));
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
                    @SuppressWarnings("unchecked")
                    List<String> requiredList = (List<String>) data.get("required");
                    if (requiredList != null) {
                        for (String mod : requiredList) {
                            requiredModsActive.add(mod.toLowerCase(Locale.ROOT));
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
        
        // Update active sets based on the new mode
        if (mode != null) {
            // Remove from all active sets first
            requiredModsActive.remove(modId);
            blacklistedModsActive.remove(modId);
            whitelistedModsActive.remove(modId);
            
            // Add to appropriate set based on mode
            String modeLower = mode.toLowerCase();
            if ("required".equals(modeLower)) {
                requiredModsActive.add(modId);
            } else if ("blacklisted".equals(modeLower)) {
                blacklistedModsActive.add(modId);
            } else if ("allowed".equals(modeLower) || "whitelisted".equals(modeLower)) {
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
            // Also remove from active sets
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
        // Default behavior based on whitelist mode
        String defaultModeStr = whitelist ? "blacklisted" : "allowed";
        return new ModConfig(defaultModeStr, "kick", null);
    }

    public void addAllMods(Set<String> mods, String mode, String action, String warnMessage) {
        String modeLower = mode.toLowerCase();
        for (String mod : mods) {
            String modId = mod.toLowerCase(Locale.ROOT);
            modConfigMap.put(modId, new ModConfig(mode, action, warnMessage));
            
            // Also add to appropriate active set
            // Remove from all first
            requiredModsActive.remove(modId);
            blacklistedModsActive.remove(modId);
            whitelistedModsActive.remove(modId);
            
            // Add to appropriate set
            if ("required".equals(modeLower)) {
                requiredModsActive.add(modId);
            } else if ("blacklisted".equals(modeLower)) {
                blacklistedModsActive.add(modId);
            } else if ("allowed".equals(modeLower) || "whitelisted".equals(modeLower)) {
                whitelistedModsActive.add(modId);
            }
        }
        save();
    }


    @SuppressWarnings("unchecked")
    public void save() {
        // Save config.yml - preserve messages section from file if it exists
        Map<String, Object> existingMessages = new LinkedHashMap<>();
        try (FileReader reader = new FileReader(configYmlFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(reader);
            if (data != null && data.containsKey("messages")) {
                Object messagesObj = data.get("messages");
                if (messagesObj instanceof Map) {
                    existingMessages.putAll((Map<String, Object>) messagesObj);
                }
            }
        } catch (IOException e) {
            // File doesn't exist yet, that's fine
        }
        
        // Update with current default messages
        existingMessages.put("kick", kickMessage);
        existingMessages.put("no-handshake", noHandshakeKickMessage);
        existingMessages.put("missing-whitelist", missingWhitelistModMessage);
        existingMessages.put("invalid-signature", invalidSignatureKickMessage);

        try (FileWriter writer = new FileWriter(configYmlFile)) {
            StringBuilder yaml = new StringBuilder();
            yaml.append("# HandShaker v4 Configuration\n");
            yaml.append("# Main plugin settings (mod-specific settings are in YAML files)\n\n");
            yaml.append("config: v4\n\n");
            yaml.append("behavior: ").append(behavior.toString().toLowerCase()).append("\n");
            yaml.append("integrity-mode: ").append(integrityMode.toString().toLowerCase()).append("\n");
            yaml.append("whitelist: ").append(whitelist).append("\n");
            yaml.append("allow-bedrock-players: ").append(allowBedrockPlayers).append("\n");
            yaml.append("playerdb-enabled: ").append(playerdbEnabled).append("\n\n");
            yaml.append("mods-required-enabled: ").append(modsRequiredEnabled).append("\n");
            yaml.append("mods-blacklisted-enabled: ").append(modsBlacklistedEnabled).append("\n");
            yaml.append("mods-whitelisted-enabled: ").append(modsWhitelistedEnabled).append("\n\n");
            yaml.append("messages:\n");
            for (Map.Entry<String, Object> entry : existingMessages.entrySet()) {
                yaml.append("  ").append(entry.getKey()).append(": \"").append(escapeYamlString(entry.getValue().toString())).append("\"\n");
            }
            writer.write(yaml.toString());
        } catch (IOException e) {
            HandShakerServer.LOGGER.error("Could not save config.yml!");
        }

        // Save mods YAML files
        saveModsYamlFiles();
    }

    private void saveModsYamlFiles() {
        // Save ignored mods
        if (!ignoredMods.isEmpty()) {
            File ignoredFile = new File(configDir, "mods-ignored.yml");
            try (FileWriter writer = new FileWriter(ignoredFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Mods which will be hidden from commands to show up\n\n");
                yaml.append("ignored:\n");
                for (String mod : ignoredMods) {
                    yaml.append("  - ").append(mod).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                HandShakerServer.LOGGER.error("Could not save mods-ignored.yml!");
            }
        }

        // Save required mods
        if (!requiredModsActive.isEmpty()) {
            File requiredFile = new File(configDir, "mods-required.yml");
            try (FileWriter writer = new FileWriter(requiredFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Required mods to join the server\n\n");
                yaml.append("required:\n");
                for (String mod : requiredModsActive) {
                    ModConfig cfg = modConfigMap.get(mod);
                    String action = cfg != null ? cfg.getActionName() : "kick";
                    yaml.append("  ").append(mod).append(": ").append(action).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                HandShakerServer.LOGGER.error("Could not save mods-required.yml!");
            }
        }

        // Save blacklisted mods
        if (!blacklistedModsActive.isEmpty()) {
            File blacklistedFile = new File(configDir, "mods-blacklisted.yml");
            try (FileWriter writer = new FileWriter(blacklistedFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Blacklisted mods: modname: action\n# If a player has any of these mods, they will be kicked\n\n");
                yaml.append("blacklisted:\n");
                for (String mod : blacklistedModsActive) {
                    ModConfig cfg = modConfigMap.get(mod);
                    String action = cfg != null ? cfg.getActionName() : "kick";
                    yaml.append("  ").append(mod).append(": ").append(action).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                HandShakerServer.LOGGER.error("Could not save mods-blacklisted.yml!");
            }
        }

        // Save whitelisted mods
        if (!whitelistedModsActive.isEmpty()) {
            File whitelistedFile = new File(configDir, "mods-whitelisted.yml");
            try (FileWriter writer = new FileWriter(whitelistedFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Whitelisted mods which are allowed but not required,\n");
                yaml.append("# but if in config.yml whitelist: true, only these mods are allowed\n\n");
                yaml.append("whitelisted:\n");
                for (String mod : whitelistedModsActive) {
                    ModConfig cfg = modConfigMap.get(mod);
                    String action = cfg != null ? cfg.getActionName() : "none";
                    yaml.append("  ").append(mod).append(": ").append(action).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                HandShakerServer.LOGGER.error("Could not save mods-whitelisted.yml!");
            }
        }
    }

    private String escapeYamlString(String str) {
        return str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
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

        Set<String> missingRequired = new HashSet<>();
        Set<String> blacklistedFound = new HashSet<>();

        // Check required mods (only if enabled)
        if (modsRequiredEnabled) {
            for (String modId : requiredModsActive) {
                if (!info.mods().contains(modId)) {
                    missingRequired.add(modId);
                }
            }
        }

        // Check blacklisted mods (only if enabled)
        if (modsBlacklistedEnabled) {
            for (String modId : blacklistedModsActive) {
                if (info.mods().contains(modId)) {
                    blacklistedFound.add(modId);
                }
            }
        }

        if (!missingRequired.isEmpty()) {
            String msg = missingWhitelistModMessage.replace("{mod}", String.join(", ", missingRequired));
            player.networkHandler.disconnect(net.minecraft.text.Text.literal(msg));
            return;
        }

        if (!blacklistedFound.isEmpty()) {
            // Get the first blacklisted mod to determine the action
            String firstBlacklistedMod = blacklistedFound.iterator().next();
            ModConfig modCfg = modConfigMap.get(firstBlacklistedMod.toLowerCase(Locale.ROOT));
            
            if (modCfg != null) {
                String actionName = modCfg.getActionName() != null ? modCfg.getActionName().toLowerCase(Locale.ROOT) : "kick";
                ActionDefinition actionDef = actionsMap.get(actionName);
                
                HandShakerServer.LOGGER.info("Blacklisted mod detected: {}. Action: '{}'. ActionDef exists: {}. ActionDef empty: {}", 
                    firstBlacklistedMod, actionName, actionDef != null, (actionDef != null && actionDef.isEmpty()));
                
                if (actionDef != null && !actionDef.isEmpty()) {
                    // Execute custom action commands
                    if (actionDef.shouldLog()) {
                        HandShakerServer.LOGGER.info("Executing action '{}' for player {} (blacklisted mods: {})", 
                            actionName, player.getName().getString(), blacklistedFound);
                    }
                    
                    for (String command : actionDef.getCommands()) {
                        String expandedCommand = expandCommandPlaceholders(command, player.getName().getString(), String.join(", ", blacklistedFound));
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
                    
                    // Still kick the player after executing the action
                    String msg = kickMessage.replace("{mod}", String.join(", ", blacklistedFound));
                    player.networkHandler.disconnect(net.minecraft.text.Text.literal(msg));
                } else {
                    // Fall back to default kick if action doesn't exist or is empty
                    String msg = kickMessage.replace("{mod}", String.join(", ", blacklistedFound));
                    player.networkHandler.disconnect(net.minecraft.text.Text.literal(msg));
                }
            } else {
                // Fall back to default kick if no config found
                String msg = kickMessage.replace("{mod}", String.join(", ", blacklistedFound));
                player.networkHandler.disconnect(net.minecraft.text.Text.literal(msg));
            }
        }
        
        // Check allowed/whitelisted mods and execute their actions
        if ( executeActions && modsWhitelistedEnabled) {
            Set<String> allowedFound = new HashSet<>();
            for (String modId : info.mods()) {
                String modIdLower = modId.toLowerCase(Locale.ROOT);
                ModConfig cfg = modConfigMap.get(modIdLower);
                if (cfg != null && cfg.isAllowed()) {
                    String actionName = cfg.getActionName();
                    if (actionName != null && !actionName.equals("none")) {
                        allowedFound.add(modIdLower);
                    }
                }
            }
        
        if (!allowedFound.isEmpty()) {
            // Only execute actions once per login session
            // Check all allowed mods and execute actions for each one that has a valid action
            for (String allowedMod : allowedFound) {
                ModConfig modCfg = modConfigMap.get(allowedMod.toLowerCase(Locale.ROOT));
                
                if (modCfg != null) {
                    String actionName = modCfg.getActionName();
                    if (actionName != null && !actionName.isEmpty()) {
                        ActionDefinition actionDef = actionsMap.get(actionName.toLowerCase(Locale.ROOT));
                        if (HandShakerServer.DEBUG_MODE) {
                            HandShakerServer.LOGGER.info("Allowed mod detected: {}. Action: '{}'. ActionDef exists: {}. ActionDef empty: {}", 
                                allowedMod, actionName, actionDef != null, (actionDef != null && actionDef.isEmpty()));
                        }
                        
                        if (actionDef != null && !actionDef.isEmpty()) {
                            // Execute custom action commands
                            if (actionDef.shouldLog()) {
                                HandShakerServer.LOGGER.info("Executing action '{}' for player {} (allowed mod: {})", 
                                    actionName, player.getName().getString(), allowedMod);
                            }
                            
                            for (String command : actionDef.getCommands()) {
                                String expandedCommand = expandCommandPlaceholders(command, player.getName().getString(), allowedMod);
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
        }
        }
    }

    public void playerLeft(ServerPlayerEntity player) {
    }

    private String expandCommandPlaceholders(String command, String playerName, String modName) {
        // Replace {messages.xxx} placeholders FIRST
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{messages\\.([^}]+)\\}");
        java.util.regex.Matcher matcher = pattern.matcher(command);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String messageKey = matcher.group(1);
            String messageValue = messagesMap.getOrDefault(messageKey, "{messages." + messageKey + "}");
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(messageValue));
        }
        matcher.appendTail(sb);
        String result = sb.toString();
        
        // THEN replace player and mod placeholders (including those inside messages)
        result = result
            .replace("{player}", playerName)
            .replace("{mod}", modName)
            .replace("{mods}", modName);
        
        return result;
    }
}
