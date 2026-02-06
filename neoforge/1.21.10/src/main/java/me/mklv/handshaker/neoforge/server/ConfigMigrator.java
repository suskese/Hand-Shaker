package me.mklv.handshaker.neoforge.server;

import com.google.gson.*;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigMigrator {
    private final Path configDir;
    private final Logger logger;

    public ConfigMigrator(Path configDir, Logger logger) {
        this.configDir = configDir;
        this.logger = logger;
    }

    public boolean migrateIfNeeded() {
        File v3ConfigFile = configDir.resolve("hand-shaker.json").toFile();
        File configYml = configDir.resolve("config.yml").toFile();
        File modsYml = configDir.resolve("mods.yml").toFile();

        // If both v4 files exist, assume already migrated
        if (configYml.exists() && modsYml.exists()) {
            // Clean up old v3 config if it still exists
            if (v3ConfigFile.exists()) {
                v3ConfigFile.delete();
            }
            return false;
        }

        // If v3 config exists, migrate it
        if (v3ConfigFile.exists()) {
            logger.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            logger.warn("Detected v3 hand-shaker.json - Starting migration to v4");
            logger.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            try {
                migrateV3ToV4(v3ConfigFile, configYml, modsYml);
                backupOldConfig(v3ConfigFile);
                backupDatabase(configDir.toFile());
                
                // Delete the original v3 config after successful migration
                v3ConfigFile.delete();
                
                logger.warn("✓ Migration completed successfully!");
                logger.warn("  - Config split: config.yml (main settings) + mods.yml (mod-specific)");
                logger.warn("  - Old config backed up: hand-shaker-v3.json.bak");
                logger.warn("  - Database backed up: hand-shaker-history-v3.db.bak");
                logger.warn("  - Original hand-shaker.json deleted");
                logger.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                return true;
            } catch (Exception e) {
                logger.error("Failed to migrate v3 config: {}", e.getMessage());
                return false;
            }
        }

        return false;
    }

    private void migrateV3ToV4(File v3ConfigFile, File configYml, File modsYml) throws IOException {
        // Parse v3 JSON config
        JsonObject v3Config;
        try (FileReader reader = new FileReader(v3ConfigFile)) {
            v3Config = JsonParser.parseReader(reader).getAsJsonObject();
        }

        // Create v4 config YAML content
        StringBuilder configYamlContent = new StringBuilder();
        configYamlContent.append("config: v4\n");
        configYamlContent.append("# Hand-Shaker Configuration\n\n");

        // Behavior setting
        String behavior = v3Config.has("Behavior") ? v3Config.get("Behavior").getAsString() : "Strict";
        configYamlContent.append("# Behavior: strict (kick immediately) or permissive (log only)\n");
        configYamlContent.append("behavior: ").append(behavior.toLowerCase()).append("\n\n");

        // Integrity Mode setting
        String integrity = v3Config.has("Integrity") ? v3Config.get("Integrity").getAsString() : "Signed";
        configYamlContent.append("# Integrity: signed (verify signatures) or basic (no verification)\n");
        configYamlContent.append("integrity-mode: ").append(integrity.toLowerCase()).append("\n\n");

        // Default Mode setting
        String defaultMode = v3Config.has("Default Mode") ? v3Config.get("Default Mode").getAsString() : "allowed";
        configYamlContent.append("# Default mode: allowed (whitelist) or blocked (blacklist)\n");
        configYamlContent.append("default-mode: ").append(defaultMode.toLowerCase()).append("\n\n");

        // Bedrock Players setting
        boolean allowBedrock = v3Config.has("Allow Bedrock Players") && v3Config.get("Allow Bedrock Players").getAsBoolean();
        configYamlContent.append("# Allow Bedrock players to join\n");
        configYamlContent.append("allow-bedrock-players: ").append(allowBedrock).append("\n\n");

        // Kick Messages
        configYamlContent.append("messages:\n");
        
        String kickMsg = v3Config.has("Kick Message") ? v3Config.get("Kick Message").getAsString() : "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
        configYamlContent.append("  kick: \"").append(escapeYamlString(kickMsg)).append("\"\n");
        
        String noHandshakeMsg = v3Config.has("Missing mod message") ? v3Config.get("Missing mod message").getAsString() : "To connect to this server please download 'Hand-shaker' mod.";
        configYamlContent.append("  no-handshake: \"").append(escapeYamlString(noHandshakeMsg)).append("\"\n");
        
        String missingWhitelistMsg = v3Config.has("Missing required mod message") ? v3Config.get("Missing required mod message").getAsString() : v3Config.has("Missing whitelist mod message") ? v3Config.get("Missing whitelist mod message").getAsString() : "You are missing required mods: {mod}. Please install them to join this server.";
        configYamlContent.append("  missing-whitelist: \"").append(escapeYamlString(missingWhitelistMsg)).append("\"\n");
        
        String invalidSigMsg = v3Config.has("Invalid signature kick message") ? v3Config.get("Invalid signature kick message").getAsString() : "Invalid client signature. Please use the official client.";
        configYamlContent.append("  invalid-signature: \"").append(escapeYamlString(invalidSigMsg)).append("\"\n");

        // Save config.yml
        try (FileWriter writer = new FileWriter(configYml)) {
            writer.write(configYamlContent.toString());
        }

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

        // Create mods.yml content
        StringBuilder modsYamlContent = new StringBuilder();
        modsYamlContent.append("config: v4\n");
        modsYamlContent.append("# Mod-specific configuration\n\n");
        
        // Add ignored mods section
        modsYamlContent.append("ignored-mods:\n");
        if (!ignoredMods.isEmpty()) {
            for (String mod : ignoredMods) {
                modsYamlContent.append("  - \"").append(mod).append("\"\n");
            }
        }

        // Add configured mods
        modsYamlContent.append("\nmods:\n");
        for (String modId : modsObj.keySet()) {
            JsonElement elem = modsObj.get(modId);
            modsYamlContent.append("  ").append(modId).append(":\n");
            if (elem.isJsonObject()) {
                JsonObject modObj = elem.getAsJsonObject();
                for (String key : modObj.keySet()) {
                    String value = modObj.get(key).getAsString();
                    modsYamlContent.append("    ").append(key).append(": \"").append(escapeYamlString(value)).append("\"\n");
                }
            } else {
                modsYamlContent.append("    mode: \"").append(elem.getAsString()).append("\"\n");
                modsYamlContent.append("    action: kick\n");
            }
        }

        // Save mods.yml
        try (FileWriter writer = new FileWriter(modsYml)) {
            writer.write(modsYamlContent.toString());
        }
    }

    private String escapeYamlString(String str) {
        if (str == null) return "";
        return str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void backupOldConfig(File originalFile) throws IOException {
        File backup = new File(originalFile.getParent(), "hand-shaker-v3.json.bak");
        Files.copy(originalFile.toPath(), backup.toPath());
        logger.info("Backed up v3 config to: {}", backup.getName());
    }

    private void backupDatabase(File dataFolder) throws IOException {
        File oldDb = new File(dataFolder, "hand-shaker-history.db");
        if (oldDb.exists()) {
            File backup = new File(dataFolder, "hand-shaker-history-v3.db.bak");
            Files.copy(oldDb.toPath(), backup.toPath());
            
            logger.warn("⚠ Database backed up: {}", backup.getName());
            logger.warn("  The database schema has been optimized for better performance.");
            logger.warn("  Your old database is preserved in case you need it.");
            logger.warn("  You can safely delete the backup once you verify everything works.");
        }
    }
}
