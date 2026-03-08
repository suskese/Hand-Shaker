package me.mklv.handshaker.common.configs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

final class ConfigMigrationIoSupport {
    private ConfigMigrationIoSupport() {
    }

    static void saveModsYaml(File file, String key, List<String> mods, Map<String, String> actionMap) throws IOException {
        StringBuilder yaml = new StringBuilder();

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

        if ("ignored".equals(key)) {
            for (String mod : mods) {
                yaml.append("  - ").append(mod).append("\n");
            }
        } else {
            for (String mod : mods) {
                String action = actionMap != null ? actionMap.getOrDefault(mod, "kick") : "kick";
                yaml.append("  ").append(mod).append(": ").append(action).append("\n");
            }
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(yaml.toString());
        }
    }

    static void saveBlacklistedYaml(File file, Map<String, String> blacklistMap) throws IOException {
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

    static void saveAsYaml(File file, Map<String, Object> data) throws IOException {
        StringBuilder yaml = new StringBuilder();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.startsWith("#")) {
                yaml.append(key).append("\n");
            } else if (key.isEmpty()) {
                yaml.append("\n");
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nested = (Map<String, Object>) value;
                for (Map.Entry<String, Object> sub : nested.entrySet()) {
                    yaml.append(key).append(".").append(sub.getKey()).append(": ");
                    appendValue(yaml, sub.getValue());
                    yaml.append("\n");
                }
            } else {
                yaml.append(key).append(": ");
                appendValue(yaml, value);
                yaml.append("\n");
            }
        }

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(yaml.toString());
        }
    }

    static void backupOldConfig(File originalFile, ConfigMigration.ConfigMigrator.Logger logger) throws IOException {
        File backup = new File(originalFile.getParent(), "hand-shaker-v3.json.bak");
        Files.copy(originalFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        if (logger != null) {
            logger.info("Backed up v3 config to: " + backup.getName());
        }
    }

    static void backupDatabase(File dataFolder, ConfigMigration.ConfigMigrator.Logger logger) throws IOException {
        File sqliteDb = new File(dataFolder, "hand-shaker-history.db");
        backupIfExists(sqliteDb, new File(dataFolder, "hand-shaker-history-v3.db.bak"), logger);

        File h2Db = new File(dataFolder, "hand-shaker-history.mv.db");
        backupIfExists(h2Db, new File(dataFolder, "hand-shaker-history-v3.mv.db.bak"), logger);
    }

    private static void backupIfExists(File source, File target, ConfigMigration.ConfigMigrator.Logger logger) throws IOException {
        if (!source.exists()) {
            return;
        }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        if (logger != null) {
            logger.warn("Database backed up: " + target.getName());
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
        } else {
            sb.append(value);
        }
    }
}
