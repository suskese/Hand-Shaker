package me.mklv.handshaker.common.configs;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigMigration {
    private ConfigMigration() {
    }

    // region ConfigMigrator
    public static class ConfigMigrator {
        public interface Logger {
            void info(String message);
            void warn(String message);
            void error(String message, Throwable error);
        }

        public static boolean migrateIfNeeded(Path configDir, Logger logger) {
            if (configDir == null) {
                logWarn(logger, "Config migration skipped: config directory is null");
                return false;
            }

            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                logError(logger, "Config migration failed: could not create config directory", e);
                return false;
            }

            File v3ConfigFile = configDir.resolve("hand-shaker.json").toFile();
            File configYml = configDir.resolve("config.yml").toFile();
            File requiredYml = configDir.resolve("mods-required.yml").toFile();
            File blacklistedYml = configDir.resolve("mods-blacklisted.yml").toFile();
            File whitelistedYml = configDir.resolve("mods-whitelisted.yml").toFile();
            File ignoredYml = configDir.resolve("mods-ignored.yml").toFile();
            File requiredYmlNested = configDir.resolve("mod-lists").resolve("mods-required.yml").toFile();
            File blacklistedYmlNested = configDir.resolve("mod-lists").resolve("mods-blacklisted.yml").toFile();
            File whitelistedYmlNested = configDir.resolve("mod-lists").resolve("mods-whitelisted.yml").toFile();
            File ignoredYmlNested = configDir.resolve("mod-lists").resolve("mods-ignored.yml").toFile();

            if (configYml.exists() && (
                requiredYml.exists() || blacklistedYml.exists() || whitelistedYml.exists() || ignoredYml.exists()
                    || requiredYmlNested.exists() || blacklistedYmlNested.exists() || whitelistedYmlNested.exists() || ignoredYmlNested.exists())) {
                if (v3ConfigFile.exists()) {
                    if (!v3ConfigFile.delete()) {
                        logWarn(logger, "Could not delete old v3 config file: " + v3ConfigFile.getName());
                    }
                }
                return false;
            }

            if (!v3ConfigFile.exists()) {
                return false;
            }

            logWarn(logger, "----------------------------------------------");
            logWarn(logger, "Detected v3 hand-shaker.json - starting migration to v4");
            logWarn(logger, "----------------------------------------------");

            try {
                migrateV3ToV4(v3ConfigFile, configYml, configDir.toFile());
                backupOldConfig(v3ConfigFile, logger);
                backupDatabase(configDir.toFile(), logger);

                if (!v3ConfigFile.delete()) {
                    logWarn(logger, "Could not delete original v3 config file");
                }

                logWarn(logger, "Migration completed successfully");
                logWarn(logger, "- Config split: config.yml + mod list YAML files");
                logWarn(logger, "- Old config backed up: hand-shaker-v3.json.bak");
                logWarn(logger, "- Database backups created when present");
                logWarn(logger, "----------------------------------------------");
                return true;
            } catch (Exception e) {
                logError(logger, "Failed to migrate v3 config", e);
                return false;
            }
        }

        private static void migrateV3ToV4(File v3ConfigFile, File configYml, File dataFolder) throws IOException {
            JsonObject root;
            try (FileReader reader = new FileReader(v3ConfigFile)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    throw new IOException("Invalid JSON structure in hand-shaker.json");
                }
                root = parsed.getAsJsonObject();
            }

            Map<String, Object> mainConfig = new LinkedHashMap<>();
            mainConfig.put("# HandShaker v4 Configuration", null);
            mainConfig.put("config", "v4");
            mainConfig.put("", null);
            mainConfig.put("# This file contains main plugin settings", null);
            mainConfig.put("# Mod-specific settings are in mods YAML files", null);
            mainConfig.put("", null);

            String behavior = getStringByKeys(root, "behavior", "Behavior");
            if (behavior == null) {
                behavior = "strict";
            }
            mainConfig.put("behavior", behavior.toLowerCase());
            mainConfig.put("# Options: strict, vanilla", null);
            mainConfig.put("", null);

            String integrity = getStringByKeys(root, "integrity-mode", "Integrity");
            if (integrity == null) {
                integrity = "signed";
            }
            mainConfig.put("integrity-mode", integrity.toLowerCase());
            mainConfig.put("# Options: signed, dev", null);
            mainConfig.put("", null);

            Boolean whitelist = getBooleanByKeys(root, "whitelist");
            if (whitelist == null) {
                String defaultMode = getStringByKeys(root, "default-mode", "Default Mode");
                whitelist = defaultMode != null && defaultMode.equalsIgnoreCase("blacklisted");
            }
            mainConfig.put("whitelist", whitelist);
            mainConfig.put("# Whitelist mode: true = only allowed mods, false = allowed by default", null);
            mainConfig.put("", null);

            Boolean allowBedrock = getBooleanByKeys(root, "allow-bedrock-players", "Allow Bedrock Players");
            if (allowBedrock == null) {
                allowBedrock = false;
            }
            mainConfig.put("allow-bedrock-players", allowBedrock);
            mainConfig.put("", null);

            Boolean playerdbEnabled = getBooleanByKeys(root, "playerdb-enabled");
            if (playerdbEnabled == null) {
                playerdbEnabled = false;
            }
            mainConfig.put("playerdb-enabled", playerdbEnabled);
            mainConfig.put("", null);

            mainConfig.put("# Kick Messages - customize as needed", null);

            JsonObject messagesObj = getObjectByKeys(root, "messages");
            String kickMsg = getStringByKeys(messagesObj, "kick", "kick-message", "Kick Message");
            if (kickMsg == null) {
                kickMsg = getStringByKeys(root, "kick", "kick-message", "Kick Message");
            }
            if (kickMsg == null) {
                kickMsg = ConfigTypes.StandardMessages.DEFAULT_KICK_MESSAGE;
            }
            mainConfig.put("messages.kick", kickMsg);

            String noHandshakeMsg = getStringByKeys(messagesObj, "no-handshake", "missing-mod-message", "Missing mod message");
            if (noHandshakeMsg == null) {
                noHandshakeMsg = getStringByKeys(root, "no-handshake", "missing-mod-message", "Missing mod message");
            }
            if (noHandshakeMsg == null) {
                noHandshakeMsg = ConfigTypes.StandardMessages.DEFAULT_NO_HANDSHAKE_MESSAGE;
            }
            mainConfig.put("messages.no-handshake", noHandshakeMsg);

            String missingWhitelistMsg = getStringByKeys(messagesObj, "missing-whitelist", "missing-required-mod-message", "Missing required mod message", "Missing whitelist mod message");
            if (missingWhitelistMsg == null) {
                missingWhitelistMsg = getStringByKeys(root, "missing-whitelist", "missing-required-mod-message", "Missing required mod message", "Missing whitelist mod message");
            }
            if (missingWhitelistMsg == null) {
                missingWhitelistMsg = ConfigTypes.StandardMessages.DEFAULT_MISSING_WHITELIST_MESSAGE;
            }
            mainConfig.put("messages.missing-whitelist", missingWhitelistMsg);

            String invalidSigMsg = getStringByKeys(messagesObj, "invalid-signature", "Invalid signature kick message");
            if (invalidSigMsg == null) {
                invalidSigMsg = getStringByKeys(root, "invalid-signature", "Invalid signature kick message");
            }
            if (invalidSigMsg == null) {
                invalidSigMsg = ConfigTypes.StandardMessages.DEFAULT_INVALID_SIGNATURE_MESSAGE;
            }
            mainConfig.put("messages.invalid-signature", invalidSigMsg);

            saveAsYaml(configYml, mainConfig);

            Set<String> ignoredMods = new HashSet<>();
            addIgnoredMods(root, ignoredMods, "ignored-mods", "Ignored Mods", "ignored mods");

            Set<String> requiredMods = new HashSet<>();
            Set<String> blacklistedMods = new HashSet<>();
            Set<String> whitelistedMods = new HashSet<>();
            Map<String, String> requiredActions = new HashMap<>();
            Map<String, String> blacklistedActions = new HashMap<>();
            Map<String, String> whitelistedActions = new HashMap<>();

            boolean usedExplicitSections = false;

            JsonObject requiredObj = getObjectByKeys(root, "required-mods", "required mods");
            if (requiredObj != null) {
                usedExplicitSections = true;
                readModeMap(requiredObj, requiredMods, requiredActions);
            }

            JsonObject blacklistedObj = getObjectByKeys(root, "blacklisted-mods", "blacklisted mods");
            if (blacklistedObj != null) {
                usedExplicitSections = true;
                readModeMap(blacklistedObj, blacklistedMods, blacklistedActions);
            }

            JsonObject whitelistedObj = getObjectByKeys(root, "whitelisted-mods", "whitelisted mods");
            if (whitelistedObj != null) {
                usedExplicitSections = true;
                readModeMap(whitelistedObj, whitelistedMods, whitelistedActions);
            }

            if (!usedExplicitSections) {
                JsonObject modsObj = getObjectByKeys(root, "Mods", "mods");
                if (modsObj != null) {
                    for (Map.Entry<String, JsonElement> entry : modsObj.entrySet()) {
                        String modId = entry.getKey();
                        LegacyModEntry modEntry = readModEntry(entry.getValue());
                        if (modEntry.mode.equalsIgnoreCase("required")) {
                            requiredMods.add(modId);
                            requiredActions.put(modId, modEntry.action);
                        } else if (modEntry.mode.equalsIgnoreCase("blacklisted")) {
                            blacklistedMods.add(modId);
                            blacklistedActions.put(modId, modEntry.action);
                        } else if (modEntry.mode.equalsIgnoreCase("allowed") || modEntry.mode.equalsIgnoreCase("whitelisted")) {
                            whitelistedMods.add(modId);
                            whitelistedActions.put(modId, modEntry.action);
                        }
                    }
                }
            }

            if (!requiredMods.isEmpty()) {
                File requiredFile = new File(new File(dataFolder, "mod-lists"), "mods-required.yml");
                saveModsYaml(requiredFile, "required", new ArrayList<>(requiredMods), requiredActions);
            }

            if (!blacklistedMods.isEmpty()) {
                File blacklistedFile = new File(new File(dataFolder, "mod-lists"), "mods-blacklisted.yml");
                saveBlacklistedYaml(blacklistedFile, blacklistedActions);
            }

            if (!whitelistedMods.isEmpty()) {
                File whitelistedFile = new File(new File(dataFolder, "mod-lists"), "mods-whitelisted.yml");
                saveModsYaml(whitelistedFile, "whitelisted", new ArrayList<>(whitelistedMods), whitelistedActions);
            }

            if (!ignoredMods.isEmpty()) {
                File ignoredFile = new File(new File(dataFolder, "mod-lists"), "mods-ignored.yml");
                saveModsYaml(ignoredFile, "ignored", new ArrayList<>(ignoredMods), null);
            }
        }

        private static void addIgnoredMods(JsonObject root, Set<String> ignoredMods, String... keys) {
            JsonElement ignoredElem = getElementByKeys(root, keys);
            if (ignoredElem != null && ignoredElem.isJsonArray()) {
                for (JsonElement elem : ignoredElem.getAsJsonArray()) {
                    if (elem != null && elem.isJsonPrimitive()) {
                        ignoredMods.add(elem.getAsString());
                    }
                }
            }
        }

        private static void readModeMap(JsonObject source, Set<String> mods, Map<String, String> actions) {
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                String modId = entry.getKey();
                String action = readActionValue(entry.getValue());
                mods.add(modId);
                actions.put(modId, action);
            }
        }

        private static LegacyModEntry readModEntry(JsonElement value) {
            String mode = "allowed";
            String action = "kick";

            if (value != null && value.isJsonObject()) {
                JsonObject obj = value.getAsJsonObject();
                String rawMode = getStringByKeys(obj, "mode", "Mode");
                String rawAction = getStringByKeys(obj, "action", "Action");
                if (rawMode != null) {
                    mode = rawMode;
                }
                if (rawAction != null) {
                    action = rawAction;
                }
            } else if (value != null && value.isJsonPrimitive()) {
                mode = value.getAsString();
            }

            return new LegacyModEntry(mode, action);
        }

        private static String readActionValue(JsonElement value) {
            if (value == null) {
                return "kick";
            }
            if (value.isJsonPrimitive()) {
                return value.getAsString();
            }
            if (value.isJsonObject()) {
                String action = getStringByKeys(value.getAsJsonObject(), "action", "Action");
                if (action != null) {
                    return action;
                }
            }
            return "kick";
        }

        private static JsonObject getObjectByKeys(JsonObject obj, String... keys) {
            JsonElement element = getElementByKeys(obj, keys);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
            return null;
        }

        private static String getStringByKeys(JsonObject obj, String... keys) {
            JsonElement element = getElementByKeys(obj, keys);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsString();
            }
            return null;
        }

        private static Boolean getBooleanByKeys(JsonObject obj, String... keys) {
            JsonElement element = getElementByKeys(obj, keys);
            if (element != null && element.isJsonPrimitive()) {
                try {
                    return element.getAsBoolean();
                } catch (UnsupportedOperationException ignored) {
                    return Boolean.parseBoolean(element.getAsString());
                }
            }
            return null;
        }

        private static JsonElement getElementByKeys(JsonObject obj, String... keys) {
            if (obj == null || keys == null) {
                return null;
            }
            for (String key : keys) {
                if (key == null) {
                    continue;
                }
                JsonElement direct = obj.get(key);
                if (direct != null) {
                    return direct;
                }
            }

            Set<String> normalizedTargets = new HashSet<>();
            for (String key : keys) {
                if (key != null) {
                    normalizedTargets.add(normalizeKey(key));
                }
            }
            if (normalizedTargets.isEmpty()) {
                return null;
            }

            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String normalizedKey = normalizeKey(entry.getKey());
                if (normalizedTargets.contains(normalizedKey)) {
                    return entry.getValue();
                }
            }

            return null;
        }

        private static String normalizeKey(String key) {
            if (key == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder(key.length());
            for (int i = 0; i < key.length(); i++) {
                char ch = Character.toLowerCase(key.charAt(i));
                if (Character.isLetterOrDigit(ch)) {
                    builder.append(ch);
                }
            }
            return builder.toString();
        }

        private static void saveModsYaml(File file, String key, List<String> mods, Map<String, String> actionMap) throws IOException {
            ConfigMigrationIoSupport.saveModsYaml(file, key, mods, actionMap);
        }

        private static void saveBlacklistedYaml(File file, Map<String, String> blacklistMap) throws IOException {
            ConfigMigrationIoSupport.saveBlacklistedYaml(file, blacklistMap);
        }

        private static void saveAsYaml(File file, Map<String, Object> data) throws IOException {
            ConfigMigrationIoSupport.saveAsYaml(file, data);
        }


        private static void backupOldConfig(File originalFile, Logger logger) throws IOException {
            ConfigMigrationIoSupport.backupOldConfig(originalFile, logger);
        }

        private static void backupDatabase(File dataFolder, Logger logger) throws IOException {
            ConfigMigrationIoSupport.backupDatabase(dataFolder, logger);
        }


        private static void logWarn(Logger logger, String message) {
            if (logger != null) {
                logger.warn(message);
            }
        }

        private static void logError(Logger logger, String message, Throwable error) {
            if (logger != null) {
                logger.error(message, error);
            }
        }

        private static final class LegacyModEntry {
            private final String mode;
            private final String action;

            private LegacyModEntry(String mode, String action) {
                this.mode = mode == null ? "allowed" : mode;
                this.action = action == null ? "kick" : action;
            }
        }
    }
    // endregion
}
