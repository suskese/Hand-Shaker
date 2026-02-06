package me.mklv.handshaker.fabric.server.configs;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class ConfigMigrator {
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger("HandShaker");

    public static boolean migrateIfNeeded() {
        File dataFolder = FabricLoader.getInstance().getConfigDir().toFile();
        File v3ConfigFile = new File(dataFolder, "hand-shaker.json");
        File configYml = new File(dataFolder, "config.yml");
        File requiredYml = new File(dataFolder, "mods-required.yml");
        File blacklistedYml = new File(dataFolder, "mods-blacklisted.yml");
        File whitelistedYml = new File(dataFolder, "mods-whitelisted.yml");
        File ignoredYml = new File(dataFolder, "mods-ignored.yml");

        // Check if v4 YAML files already exist (assume already migrated)
        if (configYml.exists() && (requiredYml.exists() || blacklistedYml.exists() || whitelistedYml.exists() || ignoredYml.exists())) {
            // Clean up old v3 config if it still exists
            if (v3ConfigFile.exists()) {
                v3ConfigFile.delete();
                logger.info("Cleaned up old v3 config file");
            }
            return false;
        }

        // If v3 config exists, migrate it
        if (v3ConfigFile.exists()) {
            logger.warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.warning("Detected v3 hand-shaker.json - Starting migration to v4");
            logger.warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            try {
                migrateV3ToV4(v3ConfigFile, configYml, dataFolder);
                backupOldConfig(v3ConfigFile);
                backupDatabase(dataFolder);
                
                // Delete the original v3 config after successful migration
                v3ConfigFile.delete();
                
                logger.warning("✓ Migration completed successfully!");
                logger.warning("  - Config split: config.yml (main settings) + YAML files (mod-specific)");
            logger.warning("  - Mod lists: mods-required.yml, mods-blacklisted.yml, mods-whitelisted.yml, mods-ignored.yml");
                logger.warning("  - Old config backed up: hand-shaker-v3.json.bak");
                logger.warning("  - Database backed up: hand-shaker-history-v3.db.bak");
                logger.warning("  - Original hand-shaker.json deleted");
                logger.warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                return true;
            } catch (Exception e) {
                logger.severe("Failed to migrate v3 config: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }

        return false;
    }

    private static void migrateV3ToV4(File v3ConfigFile, File configYml, File dataFolder) throws IOException {
        // Parse v3 JSON config
        JsonObject v3Config = null;
        try (FileReader reader = new FileReader(v3ConfigFile)) {
            v3Config = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // Extract main settings
        Map<String, Object> mainConfig = new LinkedHashMap<>();
        
        mainConfig.put("# HandShaker v4 Configuration", null);
        mainConfig.put("config", "v4");
        mainConfig.put("", null);
        mainConfig.put("# This file contains main plugin settings", null);
        mainConfig.put("# Mod-specific settings are in mods YAML files", null);
        mainConfig.put("", null);

        // Behavior setting
        String behavior = v3Config.has("Behavior") ? v3Config.get("Behavior").getAsString() : "Strict";
        mainConfig.put("behavior", behavior.toLowerCase());
        mainConfig.put("# Options: strict, vanilla", null);
        mainConfig.put("", null);

        // Integrity Mode setting
        String integrity = v3Config.has("Integrity") ? v3Config.get("Integrity").getAsString() : "Signed";
        mainConfig.put("integrity-mode", integrity.toLowerCase());
        mainConfig.put("# Options: signed, dev", null);
        mainConfig.put("", null);

        // Default Mode setting
        String defaultMode = v3Config.has("Default Mode") ? v3Config.get("Default Mode").getAsString() : "allowed";
        boolean whitelist = defaultMode.equalsIgnoreCase("blacklisted");
        mainConfig.put("whitelist", whitelist);
        mainConfig.put("# Whitelist mode: true = only allowed mods, false = allowed by default", null);
        mainConfig.put("", null);

        // Bedrock Players setting
        boolean allowBedrock = v3Config.has("Allow Bedrock Players") && v3Config.get("Allow Bedrock Players").getAsBoolean();
        mainConfig.put("allow-bedrock-players", allowBedrock);
        mainConfig.put("", null);

        // Player DB enabled (default false for security)
        mainConfig.put("playerdb-enabled", false);
        mainConfig.put("", null);

        // Kick Messages
        mainConfig.put("# Kick Messages - customize as needed", null);
        String kickMsg = v3Config.has("Kick Message") ? v3Config.get("Kick Message").getAsString() : "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
        mainConfig.put("messages.kick", kickMsg);
        
        String noHandshakeMsg = v3Config.has("Missing mod message") ? v3Config.get("Missing mod message").getAsString() : "To connect to this server please download 'Hand-shaker' mod.";
        mainConfig.put("messages.no-handshake", noHandshakeMsg);
        
        String missingWhitelistMsg = v3Config.has("Missing required mod message") ? v3Config.get("Missing required mod message").getAsString() : v3Config.has("Missing whitelist mod message") ? v3Config.get("Missing whitelist mod message").getAsString() : "You are missing required mods: {mod}. Please install them to join this server.";
        mainConfig.put("messages.missing-whitelist", missingWhitelistMsg);
        
        String invalidSigMsg = v3Config.has("Invalid signature kick message") ? v3Config.get("Invalid signature kick message").getAsString() : "Invalid client signature. Please use the official client.";
        mainConfig.put("messages.invalid-signature", invalidSigMsg);

        // Save config.yml
        saveAsYaml(configYml, mainConfig);

        // Extract mod-specific settings
        JsonObject modsObj = new JsonObject();
        if (v3Config.has("Mods")) {
            modsObj = v3Config.getAsJsonObject("Mods");
        }

        // Extract ignored mods
        Set<String> ignoredMods = new HashSet<>();
        if (v3Config.has("Ignored Mods") && v3Config.get("Ignored Mods").isJsonArray()) {
            for (JsonElement elem : v3Config.getAsJsonArray("Ignored Mods")) {
                ignoredMods.add(elem.getAsString());
            }
        }

        // Split mods into categories and save as YAML files
        Set<String> requiredMods = new HashSet<>();
        Set<String> blacklistedMods = new HashSet<>();
        Set<String> whitelistedMods = new HashSet<>();
        Map<String, String> requiredActions = new HashMap<>();
        Map<String, String> blacklistActions = new HashMap<>();
        Map<String, String> whitelistedActions = new HashMap<>();

        for (String modId : modsObj.keySet()) {
            JsonElement elem = modsObj.get(modId);
            String mode = "allowed";
            String action = "kick";
            
            if (elem.isJsonObject()) {
                JsonObject modObj = elem.getAsJsonObject();
                mode = modObj.has("mode") ? modObj.get("mode").getAsString() : "allowed";
                action = modObj.has("action") ? modObj.get("action").getAsString() : "kick";
            } else {
                mode = elem.getAsString();
            }

            if (mode.equalsIgnoreCase("required")) {
                requiredMods.add(modId);
                requiredActions.put(modId, action);
            } else if (mode.equalsIgnoreCase("blacklisted")) {
                blacklistedMods.add(modId);
                blacklistActions.put(modId, action);
            } else if (mode.equalsIgnoreCase("allowed") || mode.equalsIgnoreCase("whitelisted")) {
                whitelistedMods.add(modId);
                whitelistedActions.put(modId, action);
            }
        }

        // Save mods-required.yml
        if (!requiredMods.isEmpty()) {
            File requiredFile = new File(dataFolder, "mods-required.yml");
            saveModsYaml(requiredFile, "required", new ArrayList<>(requiredMods), requiredActions);
        }

        // Save mods-blacklisted.yml
        if (!blacklistedMods.isEmpty()) {
            File blacklistedFile = new File(dataFolder, "mods-blacklisted.yml");
            Map<String, String> blacklistMap = new HashMap<>();
            for (String mod : blacklistedMods) {
                blacklistMap.put(mod, blacklistActions.getOrDefault(mod, "kick"));
            }
            saveBlacklistedYaml(blacklistedFile, blacklistMap);
        }

        // Save mods-whitelisted.yml
        if (!whitelistedMods.isEmpty()) {
            File whitelistedFile = new File(dataFolder, "mods-whitelisted.yml");
            saveModsYaml(whitelistedFile, "whitelisted", new ArrayList<>(whitelistedMods), whitelistedActions);
        }

        // Save mods-ignored.yml
        if (!ignoredMods.isEmpty()) {
            File ignoredFile = new File(dataFolder, "mods-ignored.yml");
            saveModsYaml(ignoredFile, "ignored", new ArrayList<>(ignoredMods), null);
        }
    }

    private static void saveModsYaml(File file, String key, List<String> mods, Map<String, String> actionMap) throws IOException {
        StringBuilder yaml = new StringBuilder();
        
        // Add comment header
        if ("required".equals(key)) {
            yaml.append("# Required mods to join the server\n");
            yaml.append("# Players must have ALL of these mods installed\n");
            yaml.append("# Format: modname: action (action is from mods-actions.yml or default 'kick')\n\n");
        } else if ("blacklisted".equals(key)) {
            yaml.append("# Blacklisted mods: modname: action\n");
            yaml.append("# If a player has any of these mods, the specified action will be executed\n\n");
        } else if ("whitelisted".equals(key)) {
            yaml.append("# Whitelisted mods which are allowed but not required\n");
            yaml.append("# Format: modname: action (where action is from mods-actions.yml)\n");
            yaml.append("# Only used when mods-whitelisted-enabled: true in config.yml\n\n");
        } else if ("ignored".equals(key)) {
            yaml.append("# Mods which will be hidden from commands\n\n");
        }
        
        yaml.append(key).append(":\n");
        
        // Ignored mods use list format, everything else uses map format
        if ("ignored".equals(key)) {
            for (String mod : mods) {
                yaml.append("  - ").append(mod).append("\n");
            }
        } else {
            // Map format for required, blacklisted, whitelisted
            for (String mod : mods) {
                String action = actionMap != null ? actionMap.getOrDefault(mod, "kick") : "kick";
                yaml.append("  ").append(mod).append(": ").append(action).append("\n");
            }
        }
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(yaml.toString());
        }
    }

    private static void saveBlacklistedYaml(File file, Map<String, String> blacklistMap) throws IOException {
        StringBuilder yaml = new StringBuilder();
        yaml.append("# Blacklisted mods: modname: kick/ban\n\n");
        yaml.append("blacklisted:\n");
        
        for (Map.Entry<String, String> entry : blacklistMap.entrySet()) {
            yaml.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(yaml.toString());
        }
    }

    private static void saveAsYaml(File file, Map<String, Object> data) throws IOException {
        StringBuilder yaml = new StringBuilder();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (key.startsWith("#")) {
                // Comment
                yaml.append(key).append("\n");
            } else if (key.isEmpty()) {
                // Empty line
                yaml.append("\n");
            } else if (value instanceof Map) {
                // Nested map (for messages)
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                for (Map.Entry<String, Object> sub : nested.entrySet()) {
                    yaml.append(key).append(".").append(sub.getKey()).append(": ");
                    appendValue(yaml, sub.getValue());
                    yaml.append("\n");
                }
            } else {
                // Regular key-value
                yaml.append(key).append(": ");
                appendValue(yaml, value);
                yaml.append("\n");
            }
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(yaml.toString());
        }
    }

    private static void appendValue(StringBuilder sb, Object value) {
        if (value instanceof String) {
            String str = (String) value;
            if (str.contains(":") || str.contains("#") || str.contains("\"")) {
                sb.append("\"").append(str.replace("\"", "\\\"")).append("\"");
            } else {
                sb.append(str);
            }
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Number) {
            sb.append(value);
        } else {
            sb.append(value);
        }
    }

    private static void backupOldConfig(File originalFile) throws IOException {
        File backup = new File(originalFile.getParent(), "hand-shaker-v3.json.bak");
        Files.copy(originalFile.toPath(), backup.toPath());
        logger.info("Backed up v3 config to: " + backup.getName());
    }

    private static void backupDatabase(File dataFolder) throws IOException {
        File oldDb = new File(dataFolder, "hand-shaker-history.db");
        if (oldDb.exists()) {
            File backup = new File(dataFolder, "hand-shaker-history-v3.db.bak");
            Files.copy(oldDb.toPath(), backup.toPath());
            
            logger.warning("⚠ Database backed up: " + backup.getName());
            logger.warning("  The database schema has been optimized for better performance.");
            logger.warning("  Your old database is preserved in case you need it.");
            logger.warning("  You can safely delete the backup once you verify everything works.");
        }
    }
}
