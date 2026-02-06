package me.mklv.handshaker.paper.configs;

import com.google.gson.*;
import me.mklv.handshaker.paper.HandShakerPlugin;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigMigrator {
    private final HandShakerPlugin plugin;

    public ConfigMigrator(HandShakerPlugin plugin) {
        this.plugin = plugin;
    }

    public void migrateIfNeeded() {
        File dataFolder = plugin.getDataFolder();
        dataFolder.mkdirs();
        
        File oldJsonConfig = new File(dataFolder, "hand-shaker.json");
        if (oldJsonConfig.exists()) {
            plugin.getLogger().info("Found old v3 configuration (hand-shaker.json), attempting migration to v4...");
            try {
                migrateFromJsonToYaml(oldJsonConfig);
                backupOldConfig(oldJsonConfig);
                plugin.getLogger().info("✓ Configuration successfully migrated to v4 YAML format");
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to migrate configuration: " + e.getMessage());
                plugin.getLogger().severe("Please manually convert hand-shaker.json or delete it to use defaults");
            }
        }
    }

    private void migrateFromJsonToYaml(File jsonFile) throws IOException {
        try (FileReader reader = new FileReader(jsonFile)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                throw new IOException("Invalid JSON structure in hand-shaker.json");
            }
            
            JsonObject root = element.getAsJsonObject();
            
            // Migrate main config.yml
            migrateCoreConfig(root);
            
            // Migrate mod lists
            migrateModLists(root);
            
            // Migrate actions
            migrateActions(root);
        }
    }

    private void migrateCoreConfig(JsonObject root) throws IOException {
        File dataFolder = plugin.getDataFolder();
        File configYmlFile = new File(dataFolder, "config.yml");
        
        // Try to preserve existing config.yml if it exists
        if (configYmlFile.exists()) {
            return;
        }
        
        Map<String, Object> config = new LinkedHashMap<>();
        
        // Copy behavior setting
        if (root.has("behavior") && !root.get("behavior").isJsonNull()) {
            config.put("behavior", root.get("behavior").getAsString());
        } else {
            config.put("behavior", "STRICT");
        }
        
        // Copy integrity-mode
        if (root.has("integrity-mode") && !root.get("integrity-mode").isJsonNull()) {
            config.put("integrity-mode", root.get("integrity-mode").getAsString());
        } else {
            config.put("integrity-mode", "SIGNED");
        }
        
        // Copy whitelist setting
        if (root.has("whitelist") && root.get("whitelist").isJsonPrimitive()) {
            config.put("whitelist", root.get("whitelist").getAsBoolean());
        } else {
            config.put("whitelist", false);
        }
        
        // Copy allow-bedrock-players
        if (root.has("allow-bedrock-players") && root.get("allow-bedrock-players").isJsonPrimitive()) {
            config.put("allow-bedrock-players", root.get("allow-bedrock-players").getAsBoolean());
        } else {
            config.put("allow-bedrock-players", false);
        }
        
        // Copy playerdb-enabled
        if (root.has("playerdb-enabled") && root.get("playerdb-enabled").isJsonPrimitive()) {
            config.put("playerdb-enabled", root.get("playerdb-enabled").getAsBoolean());
        } else {
            config.put("playerdb-enabled", false);
        }
        
        // Migrate messages
        Map<String, Object> messages = new LinkedHashMap<>();
        if (root.has("messages") && root.get("messages").isJsonObject()) {
            JsonObject msgObj = root.getAsJsonObject("messages");
            for (String key : msgObj.keySet()) {
                if (!msgObj.get(key).isJsonNull()) {
                    messages.put(key, msgObj.get(key).getAsString());
                }
            }
        }
        
        if (!messages.isEmpty()) {
            config.put("messages", messages);
        }
        
        Yaml yaml = new Yaml();
        try (FileWriter writer = new FileWriter(configYmlFile)) {
            yaml.dump(config, writer);
        }
        
        plugin.getLogger().info("✓ Migrated config.yml");
    }

    private void migrateModLists(JsonObject root) throws IOException {
        File dataFolder = plugin.getDataFolder();
        
        // Migrate required mods
        if (root.has("required-mods") && root.get("required-mods").isJsonObject()) {
            JsonObject requiredObj = root.getAsJsonObject("required-mods");
            if (!requiredObj.isEmpty()) {
                Map<String, Object> required = new LinkedHashMap<>();
                Map<String, Object> requiredMods = new LinkedHashMap<>();
                
                for (String modId : requiredObj.keySet()) {
                    String action = "kick";
                    if (requiredObj.get(modId).isJsonObject()) {
                        JsonObject modObj = requiredObj.getAsJsonObject(modId);
                        if (modObj.has("action") && !modObj.get("action").isJsonNull()) {
                            action = modObj.get("action").getAsString();
                        }
                    } else if (!requiredObj.get(modId).isJsonNull()) {
                        action = requiredObj.get(modId).getAsString();
                    }
                    requiredMods.put(modId, action);
                }
                
                required.put("required", requiredMods);
                
                File requiredFile = new File(dataFolder, "mods-required.yml");
                Yaml yaml = new Yaml();
                try (FileWriter writer = new FileWriter(requiredFile)) {
                    yaml.dump(required, writer);
                }
                plugin.getLogger().info("✓ Migrated mods-required.yml");
            }
        }
        
        // Migrate blacklisted mods
        if (root.has("blacklisted-mods") && root.get("blacklisted-mods").isJsonObject()) {
            JsonObject blacklistedObj = root.getAsJsonObject("blacklisted-mods");
            if (!blacklistedObj.isEmpty()) {
                Map<String, Object> blacklisted = new LinkedHashMap<>();
                Map<String, Object> blacklistedMods = new LinkedHashMap<>();
                
                for (String modId : blacklistedObj.keySet()) {
                    String action = "kick";
                    if (blacklistedObj.get(modId).isJsonObject()) {
                        JsonObject modObj = blacklistedObj.getAsJsonObject(modId);
                        if (modObj.has("action") && !modObj.get("action").isJsonNull()) {
                            action = modObj.get("action").getAsString();
                        }
                    } else if (!blacklistedObj.get(modId).isJsonNull()) {
                        action = blacklistedObj.get(modId).getAsString();
                    }
                    blacklistedMods.put(modId, action);
                }
                
                blacklisted.put("blacklisted", blacklistedMods);
                
                File blacklistedFile = new File(dataFolder, "mods-blacklisted.yml");
                Yaml yaml = new Yaml();
                try (FileWriter writer = new FileWriter(blacklistedFile)) {
                    yaml.dump(blacklisted, writer);
                }
                plugin.getLogger().info("✓ Migrated mods-blacklisted.yml");
            }
        }
        
        // Migrate whitelisted mods
        if (root.has("whitelisted-mods") && root.get("whitelisted-mods").isJsonObject()) {
            JsonObject whitelistedObj = root.getAsJsonObject("whitelisted-mods");
            if (!whitelistedObj.isEmpty()) {
                Map<String, Object> whitelisted = new LinkedHashMap<>();
                Map<String, Object> whitelistedMods = new LinkedHashMap<>();
                
                for (String modId : whitelistedObj.keySet()) {
                    String action = "none";
                    if (whitelistedObj.get(modId).isJsonObject()) {
                        JsonObject modObj = whitelistedObj.getAsJsonObject(modId);
                        if (modObj.has("action") && !modObj.get("action").isJsonNull()) {
                            action = modObj.get("action").getAsString();
                        }
                    } else if (!whitelistedObj.get(modId).isJsonNull()) {
                        action = whitelistedObj.get(modId).getAsString();
                    }
                    whitelistedMods.put(modId, action);
                }
                
                whitelisted.put("whitelisted", whitelistedMods);
                
                File whitelistedFile = new File(dataFolder, "mods-whitelisted.yml");
                Yaml yaml = new Yaml();
                try (FileWriter writer = new FileWriter(whitelistedFile)) {
                    yaml.dump(whitelisted, writer);
                }
                plugin.getLogger().info("✓ Migrated mods-whitelisted.yml");
            }
        }
        
        // Migrate ignored mods
        if (root.has("ignored-mods") && root.get("ignored-mods").isJsonArray()) {
            JsonArray ignoredArray = root.getAsJsonArray("ignored-mods");
            if (ignoredArray.size() > 0) {
                List<String> ignoredList = new ArrayList<>();
                for (JsonElement elem : ignoredArray) {
                    if (!elem.isJsonNull()) {
                        ignoredList.add(elem.getAsString());
                    }
                }
                
                if (!ignoredList.isEmpty()) {
                    Map<String, Object> ignored = new LinkedHashMap<>();
                    ignored.put("ignored", ignoredList);
                    
                    File ignoredFile = new File(dataFolder, "mods-ignored.yml");
                    Yaml yaml = new Yaml();
                    try (FileWriter writer = new FileWriter(ignoredFile)) {
                        yaml.dump(ignored, writer);
                    }
                    plugin.getLogger().info("✓ Migrated mods-ignored.yml");
                }
            }
        }
    }

    private void migrateActions(JsonObject root) throws IOException {
        if (!root.has("actions") || !root.get("actions").isJsonObject()) {
            return;
        }
        
        File dataFolder = plugin.getDataFolder();
        File actionsFile = new File(dataFolder, "mods-actions.yml");
        
        if (actionsFile.exists()) {
            return;
        }
        
        JsonObject actionsObj = root.getAsJsonObject("actions");
        Map<String, Object> actions = new LinkedHashMap<>();
        Map<String, Object> actionsMap = new LinkedHashMap<>();
        
        for (String actionName : actionsObj.keySet()) {
            JsonElement actionElem = actionsObj.get(actionName);
            
            if (actionElem.isJsonObject()) {
                JsonObject actionObj = actionElem.getAsJsonObject();
                Map<String, Object> actionData = new LinkedHashMap<>();
                
                // Copy commands
                if (actionObj.has("commands") && actionObj.get("commands").isJsonArray()) {
                    List<String> commands = new ArrayList<>();
                    for (JsonElement cmdElem : actionObj.getAsJsonArray("commands")) {
                        if (!cmdElem.isJsonNull()) {
                            commands.add(cmdElem.getAsString());
                        }
                    }
                    actionData.put("commands", commands);
                }
                
                // Copy log setting
                if (actionObj.has("log") && actionObj.get("log").isJsonPrimitive()) {
                    actionData.put("log", actionObj.get("log").getAsBoolean());
                }
                
                if (!actionData.isEmpty()) {
                    actionsMap.put(actionName, actionData);
                }
            }
        }
        
        if (!actionsMap.isEmpty()) {
            actions.put("actions", actionsMap);
            
            Yaml yaml = new Yaml();
            try (FileWriter writer = new FileWriter(actionsFile)) {
                yaml.dump(actions, writer);
            }
            plugin.getLogger().info("✓ Migrated mods-actions.yml");
        }
    }

    private void backupOldConfig(File oldJsonConfig) throws IOException {
        Path oldPath = oldJsonConfig.toPath();
        Path backupPath = oldPath.getParent().resolve("hand-shaker.json.backup");
        Files.copy(oldPath, backupPath);
        plugin.getLogger().info("Backed up old config to hand-shaker.json.backup");
    }
}
