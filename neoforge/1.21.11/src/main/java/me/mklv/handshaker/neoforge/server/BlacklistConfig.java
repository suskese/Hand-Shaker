package me.mklv.handshaker.neoforge.server;

import me.mklv.handshaker.neoforge.server.configs.ActionDefinition;
import net.neoforged.fml.loading.FMLPaths;
import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

@SuppressWarnings("unchecked")
public class BlacklistConfig {
    private final File configDir;
    private File configYmlFile;

    public enum Behavior { STRICT, VANILLA }
    public enum IntegrityMode { SIGNED, DEV }
    public enum ModStatus { REQUIRED, ALLOWED, BLACKLISTED }
    
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
            this.mode = mode != null ? mode : "allowed";
            this.action = action != null ? action : "kick";
            this.warnMessage = warnMessage;
        }

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public Action getAction() { return Action.fromString(action); }
        public void setAction(String action) { this.action = action; }
        public String getWarnMessage() { return warnMessage; }
        public void setWarnMessage(String warnMessage) { this.warnMessage = warnMessage; }
        
        public boolean isRequired() { return "required".equalsIgnoreCase(mode); }
        public boolean isBlacklisted() { return "blacklisted".equalsIgnoreCase(mode); }
        public boolean isAllowed() { return "allowed".equalsIgnoreCase(mode); }
    }

    private Behavior behavior = Behavior.STRICT;
    private IntegrityMode integrityMode = IntegrityMode.SIGNED;
    private String kickMessage = "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private String noHandshakeKickMessage = "To connect to this server please download 'Hand-shaker' mod.";
    private String missingWhitelistModMessage = "You are missing required mods: {mod}. Please install them to join this server.";
    private String invalidSignatureKickMessage = "Invalid client signature. Please use the official client.";
    private boolean allowBedrockPlayers = false;
    private boolean playerdbEnabled = false;
    
    // Mod list toggle states
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

    public BlacklistConfig() {
        File configRootDir = FMLPaths.CONFIGDIR.get().toFile();
        this.configDir = new File(configRootDir, "HandShaker");
    }

    public void load() {
        configDir.mkdirs();

        configYmlFile = new File(configDir, "config.yml");

        // Check if v4 files exist, if not create them from defaults
        createDefaultFilesIfNotExist();

        loadConfigYml();
        loadModsYamlFiles();
    }

    private void createDefaultFilesIfNotExist() {
        // Create config.yml if doesn't exist
        if (!configYmlFile.exists()) {
            try {
                copyConfigFromJar("config.yml", configYmlFile);
                HandShakerServerMod.LOGGER.info("Created default config.yml");
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.error("Could not create config.yml: {}", e.getMessage());
            }
        }

        // Create mods YAML files if they don't exist
        String[] modsFiles = {"mods-required.yml", "mods-blacklisted.yml", "mods-whitelisted.yml", "mods-ignored.yml", "mods-actions.yml"};
        for (String filename : modsFiles) {
            File file = new File(configDir, filename);
            if (!file.exists()) {
                try {
                    copyConfigFromJar(filename, file);
                    HandShakerServerMod.LOGGER.info("Created default {}", filename);
                } catch (IOException e) {
                    HandShakerServerMod.LOGGER.warn("Could not create {}: {}", filename, e.getMessage());
                }
            }
        }
    }

    private void copyConfigFromJar(String filename, File targetFile) throws IOException {
        try (InputStream is = BlacklistConfig.class.getResourceAsStream("/configs/" + filename)) {
            if (is != null) {
                Files.copy(is, targetFile.toPath());
            } else {
                throw new IOException("Config file " + filename + " not found in JAR resources");
            }
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
                    HandShakerServerMod.LOGGER.info("Loaded integrity-mode from config: {}", integrityMode);
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
                    Map<String, Object> messages = (Map<String, Object>) data.get("messages");
                    if (messages != null) {
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
            HandShakerServerMod.LOGGER.warn("Failed to load config.yml: {}", e.getMessage());
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
                        List<String> ignoredList = (List<String>) data.get("ignored");
                        if (ignoredList != null) {
                            for (String mod : ignoredList) {
                                ignoredMods.add(mod.toLowerCase(Locale.ROOT));
                            }
                        }
                    } catch (ClassCastException e) {
                        HandShakerServerMod.LOGGER.warn("Invalid format in mods-ignored.yml, expected list format");
                    }
                }
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.warn("Failed to load mods-ignored.yml: {}", e.getMessage());
            }
        }

        // Load required mods
        File requiredFile = new File(configDir, "mods-required.yml");
        if (requiredFile.exists()) {
            try (FileReader reader = new FileReader(requiredFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("required")) {
                    try {
                        List<String> requiredList = (List<String>) data.get("required");
                        if (requiredList != null) {
                            for (String mod : requiredList) {
                                String modId = mod.toLowerCase(Locale.ROOT);
                                requiredModsActive.add(modId);
                                modConfigMap.put(modId, new ModConfig("required", "kick", null));
                            }
                        }
                    } catch (ClassCastException e) {
                        HandShakerServerMod.LOGGER.warn("Invalid format in mods-required.yml, expected list format");
                    }
                }
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.warn("Failed to load mods-required.yml: {}", e.getMessage());
            }
        }

        // Load blacklisted mods
        File blacklistedFile = new File(configDir, "mods-blacklisted.yml");
        if (blacklistedFile.exists()) {
            try (FileReader reader = new FileReader(blacklistedFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("blacklisted")) {
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
                HandShakerServerMod.LOGGER.warn("Failed to load mods-blacklisted.yml: {}", e.getMessage());
            }
        }

        // Load whitelisted mods
        File whitelistedFile = new File(configDir, "mods-whitelisted.yml");
        if (whitelistedFile.exists()) {
            try (FileReader reader = new FileReader(whitelistedFile)) {
                Map<String, Object> data = yaml.load(reader);
                if (data != null && data.containsKey("whitelisted")) {
                    try {
                        List<String> whitelistedList = (List<String>) data.get("whitelisted");
                        if (whitelistedList != null) {
                            for (String mod : whitelistedList) {
                                String modId = mod.toLowerCase(Locale.ROOT);
                                whitelistedModsActive.add(modId);
                                modConfigMap.put(modId, new ModConfig("allowed", "kick", null));
                            }
                        }
                    } catch (ClassCastException e) {
                        HandShakerServerMod.LOGGER.warn("Invalid format in mods-whitelisted.yml, expected list format");
                    }
                }
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.warn("Failed to load mods-whitelisted.yml: {}", e.getMessage());
            }
        }

        // Load actions from mods-actions.yml
        loadActionsYamlFile();
    }

    private void loadActionsYamlFile() {
        actionsMap.clear();
        Yaml yaml = new Yaml();

        File actionsFile = new File(configDir, "mods-actions.yml");
        if (actionsFile.exists()) {
            try (FileReader reader = new FileReader(actionsFile)) {
                Map<String, Object> data = yaml.load(reader);

                if (data != null && data.containsKey("actions")) {
                    Object actionsObj = data.get("actions");

                    if (actionsObj instanceof Map) {
                        Map<String, Object> actionsMap = (Map<String, Object>) actionsObj;

                        if (actionsMap != null) {
                            for (Map.Entry<String, Object> entry : actionsMap.entrySet()) {
                                String actionName = entry.getKey().toLowerCase(Locale.ROOT);
                                Object actionValue = entry.getValue();

                                if (actionValue instanceof Map) {
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
                                            List<String> cmdList = (List<String>) commandsObj;
                                            commands.addAll(cmdList);
                                        }
                                    }

                                    // Create action with log flag
                                    action = new ActionDefinition(actionName, commands, shouldLog);

                                    // Always store the action, even if empty (for reference)
                                    this.actionsMap.put(actionName, action);
                                    HandShakerServerMod.LOGGER.info("Loaded action '{}': {} commands, log={}", actionName, commands.size(), shouldLog);
                                }
                            }
                        }
                    }
                }

                if (!this.actionsMap.isEmpty()) {
                    HandShakerServerMod.LOGGER.info("Loaded {} action(s) from mods-actions.yml: {}", this.actionsMap.size(), this.actionsMap.keySet());
                } else {
                    HandShakerServerMod.LOGGER.warn("No actions found in mods-actions.yml or file is empty");
                }
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.warn("Failed to load mods-actions.yml: {}", e.getMessage());
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

    public void save() {
        // Save config.yml
        try (FileWriter writer = new FileWriter(configYmlFile)) {
            StringBuilder yaml = new StringBuilder();
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
            yaml.append("  kick: \"").append(escapeYamlString(kickMessage)).append("\"\n");
            yaml.append("  no-handshake: \"").append(escapeYamlString(noHandshakeKickMessage)).append("\"\n");
            yaml.append("  missing-whitelist: \"").append(escapeYamlString(missingWhitelistModMessage)).append("\"\n");
            yaml.append("  invalid-signature: \"").append(escapeYamlString(invalidSignatureKickMessage)).append("\"\n");
            writer.write(yaml.toString());
        } catch (IOException e) {
            HandShakerServerMod.LOGGER.error("Could not save config.yml!");
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
                yaml.append("# Mods which will be hidden from commands\n\n");
                yaml.append("ignored:\n");
                for (String mod : ignoredMods) {
                    yaml.append("  - ").append(mod).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.error("Could not save mods-ignored.yml!");
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
                    yaml.append("  - ").append(mod).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.error("Could not save mods-required.yml!");
            }
        }

        // Save blacklisted mods
        if (!blacklistedModsActive.isEmpty()) {
            File blacklistedFile = new File(configDir, "mods-blacklisted.yml");
            try (FileWriter writer = new FileWriter(blacklistedFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Blacklisted mods: modname: kick/ban\n\n");
                yaml.append("blacklisted:\n");
                for (String mod : blacklistedModsActive) {
                    ModConfig cfg = modConfigMap.get(mod);
                    String action = cfg != null ? cfg.getAction().toString().toLowerCase() : "kick";
                    yaml.append("  ").append(mod).append(": ").append(action).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.error("Could not save mods-blacklisted.yml!");
            }
        }

        // Save whitelisted mods
        if (!whitelistedModsActive.isEmpty()) {
            File whitelistedFile = new File(configDir, "mods-whitelisted.yml");
            try (FileWriter writer = new FileWriter(whitelistedFile)) {
                StringBuilder yaml = new StringBuilder();
                yaml.append("# Whitelisted mods\n\n");
                yaml.append("whitelisted:\n");
                for (String mod : whitelistedModsActive) {
                    yaml.append("  - ").append(mod).append("\n");
                }
                writer.write(yaml.toString());
            } catch (IOException e) {
                HandShakerServerMod.LOGGER.error("Could not save mods-whitelisted.yml!");
            }
        }
    }

    private String escapeYamlString(String str) {
        return str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    public void checkPlayer(net.minecraft.server.level.ServerPlayer player, HandShakerServerMod.ClientInfo info) {
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

        Set<String> missingRequired = new HashSet<>();
        Set<String> blacklistedFound = new HashSet<>();

        // Check required mods
        for (String modId : requiredModsActive) {
            if (!info.mods().contains(modId)) {
                missingRequired.add(modId);
            }
        }

        // Check blacklisted mods
        for (String modId : blacklistedModsActive) {
            if (info.mods().contains(modId)) {
                blacklistedFound.add(modId);
            }
        }

        if (!missingRequired.isEmpty()) {
            String msg = missingWhitelistModMessage.replace("{mod}", String.join(", ", missingRequired));
            player.connection.disconnect(net.minecraft.network.chat.Component.literal(msg));
            return;
        }

        if (!blacklistedFound.isEmpty()) {
            String msg = kickMessage.replace("{mod}", String.join(", ", blacklistedFound));
            player.connection.disconnect(net.minecraft.network.chat.Component.literal(msg));
        }
    }
}
