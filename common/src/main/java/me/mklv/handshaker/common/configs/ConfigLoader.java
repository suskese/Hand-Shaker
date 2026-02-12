package me.mklv.handshaker.common.configs;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigLoader {
    private static final String DEFAULT_KICK_MESSAGE =
        "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
    private static final String DEFAULT_NO_HANDSHAKE_MESSAGE =
        "To connect to this server please download 'Hand-shaker' mod.";
    private static final String DEFAULT_MISSING_WHITELIST_MESSAGE =
        "You are missing required mods: {mod}. Please install them to join this server.";
    private static final String DEFAULT_INVALID_SIGNATURE_MESSAGE =
        "Invalid client signature. Please use the official client.";

    private ConfigLoader() {
    }

    public static ConfigLoadResult load(Path configDir,
                                        Class<?> resourceBase,
                                        ConfigFileBootstrap.Logger logger,
                                        ConfigLoadOptions options) {
        ConfigLoadResult result = new ConfigLoadResult();
        if (options != null) {
            result.setModsWhitelistedEnabled(options.isDefaultModsWhitelistedEnabled());
        }

        ensureDefaults(configDir, resourceBase, logger);

        File configYml = configDir.resolve("config.yml").toFile();
        loadConfigYml(configYml, logger, result);

        loadModsYamlFiles(configDir, logger, result, options);
        loadActionsYamlFile(configDir, logger, result, options);

        return result;
    }

    private static void ensureDefaults(Path configDir,
                                       Class<?> resourceBase,
                                       ConfigFileBootstrap.Logger logger) {
        ConfigFileBootstrap.copyRequired(configDir, "config.yml", resourceBase, logger);
        String[] modsFiles = {
            "mods-required.yml",
            "mods-blacklisted.yml",
            "mods-whitelisted.yml",
            "mods-ignored.yml",
            "mods-actions.yml"
        };
        for (String filename : modsFiles) {
            ConfigFileBootstrap.copyOptional(configDir, filename, resourceBase, logger);
        }
    }

    private static void loadConfigYml(File configYml,
                                      ConfigFileBootstrap.Logger logger,
                                      ConfigLoadResult result) {
        Map<String, Object> data = readYaml(configYml, logger);
        if (data == null) {
            seedDefaultMessages(result);
            return;
        }

        if (data.containsKey("behavior")) {
            String behaviorStr = data.get("behavior").toString().toLowerCase(Locale.ROOT);
            result.setBehavior(behaviorStr.startsWith("strict")
                ? ConfigState.Behavior.STRICT
                : ConfigState.Behavior.VANILLA);
        }

        if (data.containsKey("integrity-mode")) {
            String integrityStr = data.get("integrity-mode").toString().toLowerCase(Locale.ROOT);
            result.setIntegrityMode("dev".equals(integrityStr)
                ? ConfigState.IntegrityMode.DEV
                : ConfigState.IntegrityMode.SIGNED);
        }

        if (data.containsKey("whitelist")) {
            result.setWhitelist(Boolean.parseBoolean(data.get("whitelist").toString()));
        }

        if (data.containsKey("allow-bedrock-players")) {
            result.setAllowBedrockPlayers(Boolean.parseBoolean(data.get("allow-bedrock-players").toString()));
        }

        if (data.containsKey("playerdb-enabled")) {
            result.setPlayerdbEnabled(Boolean.parseBoolean(data.get("playerdb-enabled").toString()));
        }

        if (data.containsKey("mods-required-enabled")) {
            result.setModsRequiredEnabled(Boolean.parseBoolean(data.get("mods-required-enabled").toString()));
        }

        if (data.containsKey("mods-blacklisted-enabled")) {
            result.setModsBlacklistedEnabled(Boolean.parseBoolean(data.get("mods-blacklisted-enabled").toString()));
        }

        if (data.containsKey("mods-whitelisted-enabled")) {
            result.setModsWhitelistedEnabled(Boolean.parseBoolean(data.get("mods-whitelisted-enabled").toString()));
        }

        seedDefaultMessages(result);
        Object messagesObj = data.get("messages");
        if (messagesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> messages = (Map<String, Object>) messagesObj;
            for (Map.Entry<String, Object> entry : messages.entrySet()) {
                if (entry.getValue() != null) {
                    result.getMessages().put(entry.getKey(), entry.getValue().toString());
                }
            }
        }

        result.setKickMessage(result.getMessages().getOrDefault("kick", DEFAULT_KICK_MESSAGE));
        result.setNoHandshakeKickMessage(result.getMessages().getOrDefault("no-handshake", DEFAULT_NO_HANDSHAKE_MESSAGE));
        result.setMissingWhitelistModMessage(result.getMessages().getOrDefault("missing-whitelist", DEFAULT_MISSING_WHITELIST_MESSAGE));
        result.setInvalidSignatureKickMessage(result.getMessages().getOrDefault("invalid-signature", DEFAULT_INVALID_SIGNATURE_MESSAGE));
    }

    private static void seedDefaultMessages(ConfigLoadResult result) {
        Map<String, String> messages = result.getMessages();
        messages.putIfAbsent("kick", DEFAULT_KICK_MESSAGE);
        messages.putIfAbsent("no-handshake", DEFAULT_NO_HANDSHAKE_MESSAGE);
        messages.putIfAbsent("missing-whitelist", DEFAULT_MISSING_WHITELIST_MESSAGE);
        messages.putIfAbsent("invalid-signature", DEFAULT_INVALID_SIGNATURE_MESSAGE);
        result.setKickMessage(messages.get("kick"));
        result.setNoHandshakeKickMessage(messages.get("no-handshake"));
        result.setMissingWhitelistModMessage(messages.get("missing-whitelist"));
        result.setInvalidSignatureKickMessage(messages.get("invalid-signature"));
    }

    private static void loadModsYamlFiles(Path configDir,
                                          ConfigFileBootstrap.Logger logger,
                                          ConfigLoadResult result,
                                          ConfigLoadOptions options) {
        result.getModConfigMap().clear();
        result.getIgnoredMods().clear();
        result.getWhitelistedModsActive().clear();
        result.getBlacklistedModsActive().clear();
        result.getRequiredModsActive().clear();

        File ignoredFile = configDir.resolve("mods-ignored.yml").toFile();
        Map<String, Object> ignoredData = readYaml(ignoredFile, logger);
        if (ignoredData != null && ignoredData.containsKey("ignored")) {
            Object ignoredObj = ignoredData.get("ignored");
            if (ignoredObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> ignoredList = (List<String>) ignoredObj;
                for (String mod : ignoredList) {
                    if (mod != null) {
                        result.getIgnoredMods().add(mod.toLowerCase(Locale.ROOT));
                    }
                }
            } else {
                if (logger != null) {
                    logger.warn("Invalid format in mods-ignored.yml, expected list format");
                }
            }
        }

        File requiredFile = configDir.resolve("mods-required.yml").toFile();
        loadModeFile(requiredFile,
            "required",
            ConfigState.MODE_REQUIRED,
            "kick",
            result.getModConfigMap(),
            result.getRequiredModsActive(),
            logger);

        File blacklistedFile = configDir.resolve("mods-blacklisted.yml").toFile();
        loadModeFile(blacklistedFile,
            "blacklisted",
            ConfigState.MODE_BLACKLISTED,
            "kick",
            result.getModConfigMap(),
            result.getBlacklistedModsActive(),
            logger);

        boolean loadWhitelisted = options == null
            || options.isLoadWhitelistedWhenDisabled()
            || result.areModsWhitelistedEnabled();

        if (loadWhitelisted) {
            String defaultWhitelistedAction = options != null
                ? options.getDefaultWhitelistedAction()
                : "none";
            File whitelistedFile = configDir.resolve("mods-whitelisted.yml").toFile();
            loadModeFile(whitelistedFile,
                "whitelisted",
                ConfigState.MODE_ALLOWED,
                defaultWhitelistedAction,
                result.getModConfigMap(),
                result.getWhitelistedModsActive(),
                logger);
        }

        if (options != null
            && options.isCreateWhitelistedFileWhenEnabled()
            && result.areModsWhitelistedEnabled()) {
            File whitelistedFile = configDir.resolve("mods-whitelisted.yml").toFile();
            if (!whitelistedFile.exists()) {
                createWhitelistedTemplate(whitelistedFile, logger);
            }
        }
    }

    private static void loadActionsYamlFile(Path configDir,
                                            ConfigFileBootstrap.Logger logger,
                                            ConfigLoadResult result,
                                            ConfigLoadOptions options) {
        result.getActionsMap().clear();

        File actionsFile = configDir.resolve("mods-actions.yml").toFile();
        Map<String, Object> data = readYaml(actionsFile, logger);
        if (data == null || !data.containsKey("actions")) {
            return;
        }

        Object actionsObj = data.get("actions");
        if (!(actionsObj instanceof Map)) {
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> actionsMap = (Map<String, Object>) actionsObj;
        for (Map.Entry<String, Object> entry : actionsMap.entrySet()) {
            String actionName = entry.getKey().toLowerCase(Locale.ROOT);
            Object actionValue = entry.getValue();
            if (!(actionValue instanceof Map)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> actionMap = (Map<String, Object>) actionValue;
            boolean shouldLog = false;
            if (actionMap.containsKey("log")) {
                Object logObj = actionMap.get("log");
                shouldLog = logObj instanceof Boolean
                    ? (Boolean) logObj
                    : Boolean.parseBoolean(logObj.toString());
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

            ActionDefinition action = new ActionDefinition(actionName, commands, shouldLog);
            boolean includeEmpty = options == null || options.isIncludeEmptyActions();
            if (includeEmpty || !action.isEmpty() || shouldLog) {
                result.getActionsMap().put(actionName, action);
            }
        }
    }

    private static void loadModeFile(File file,
                                     String key,
                                     String mode,
                                     String defaultAction,
                                     Map<String, ConfigState.ModConfig> modConfigMap,
                                     Set<String> activeSet,
                                     ConfigFileBootstrap.Logger logger) {
        Map<String, Object> data = readYaml(file, logger);
        if (data == null || !data.containsKey(key)) {
            return;
        }

        Object obj = data.get(key);
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String modId = entry.getKey().toLowerCase(Locale.ROOT);
                String action = entry.getValue() != null ? entry.getValue().toString() : defaultAction;
                activeSet.add(modId);
                modConfigMap.put(modId, new ConfigState.ModConfig(mode, action, null));
            }
            return;
        }

        if (obj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) obj;
            for (String mod : list) {
                if (mod == null) {
                    continue;
                }
                String modId = mod.toLowerCase(Locale.ROOT);
                activeSet.add(modId);
                modConfigMap.put(modId, new ConfigState.ModConfig(mode, defaultAction, null));
            }
            return;
        }

        if (logger != null) {
            logger.warn("Invalid format in " + file.getName() + ": expected map or list");
        }
    }

    private static void createWhitelistedTemplate(File file, ConfigFileBootstrap.Logger logger) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("# Whitelisted mods which are allowed but not required,\n");
            writer.write("# but if in config.yml whitelist: true, only these mods are allowed\n");
            writer.write("# Format: modname: action (where action is from mods-actions.yml or default 'none')\n\n");
            writer.write("whitelisted:\n");
            if (logger != null) {
                logger.info("Created mods-whitelisted.yml file (whitelisted mode enabled)");
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.warn("Failed to create mods-whitelisted.yml: " + e.getMessage());
            }
        }
    }

    private static Map<String, Object> readYaml(File file, ConfigFileBootstrap.Logger logger) {
        if (file == null || !file.exists()) {
            return null;
        }
        try (FileReader reader = new FileReader(file)) {
            Yaml yaml = new Yaml();
            return yaml.load(reader);
        } catch (IOException e) {
            if (logger != null) {
                logger.warn("Failed to load " + file.getName() + ": " + e.getMessage());
            }
            return null;
        }
    }
}
