package me.mklv.handshaker.common.configs;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigIO {
    private ConfigIO() {
    }

    // region ConfigFileBootstrap
    public static final class ConfigFileBootstrap {
        private ConfigFileBootstrap() {
        }

        public interface Logger {
            void info(String message);
            void warn(String message);
            void error(String message, Throwable error);
        }

        public static boolean copyRequired(Path configDir, String filename, Class<?> resourceBase, Logger logger) {
            return copyFromResourcesIfMissing(configDir, filename, resourceBase, logger, true);
        }

        public static boolean copyOptional(Path configDir, String filename, Class<?> resourceBase, Logger logger) {
            return copyFromResourcesIfMissing(configDir, filename, resourceBase, logger, false);
        }

        private static boolean copyFromResourcesIfMissing(Path configDir,
                                                          String filename,
                                                          Class<?> resourceBase,
                                                          Logger logger,
                                                          boolean required) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                if (logger != null) {
                    logger.error("Failed to create config directory: " + configDir, e);
                }
                if (required) {
                    throw new RuntimeException("Failed to create config directory: " + configDir, e);
                }
                return false;
            }

            Path targetFile = configDir.resolve(filename);
            if (Files.exists(targetFile)) {
                return false;
            }

            String resourcePath = "/configs/" + filename;
            try (InputStream is = resourceBase.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    String message = "Config file " + resourcePath + " not found in resources";
                    if (logger != null) {
                        logger.warn(message);
                    }
                    if (required) {
                        throw new RuntimeException(message);
                    }
                    return false;
                }
                Files.copy(is, targetFile);
                if (logger != null) {
                    logger.info("Created default " + filename + " from resources");
                }
                return true;
            } catch (IOException e) {
                if (logger != null) {
                    logger.error("Failed to copy " + filename + " from resources", e);
                }
                if (required) {
                    throw new RuntimeException("Failed to copy " + filename + " from resources", e);
                }
                return false;
            }
        }
    }
    // endregion

    // region ConfigLoader
    public static final class ConfigLoader {
        private static final String DEFAULT_KICK_MESSAGE = ConfigTypes.StandardMessages.DEFAULT_KICK_MESSAGE;
        private static final String DEFAULT_NO_HANDSHAKE_MESSAGE = ConfigTypes.StandardMessages.DEFAULT_NO_HANDSHAKE_MESSAGE;
        private static final String DEFAULT_MISSING_WHITELIST_MESSAGE = ConfigTypes.StandardMessages.DEFAULT_MISSING_WHITELIST_MESSAGE;
        private static final String DEFAULT_INVALID_SIGNATURE_MESSAGE = ConfigTypes.StandardMessages.DEFAULT_INVALID_SIGNATURE_MESSAGE;
        private static final String DEFAULT_OUTDATED_CLIENT_MESSAGE = ConfigTypes.StandardMessages.DEFAULT_OUTDATED_CLIENT_MESSAGE;

        private ConfigLoader() {
        }

        public static ConfigTypes.ConfigLoadResult load(Path configDir,
                                                        Class<?> resourceBase,
                                                        ConfigFileBootstrap.Logger logger,
                                                        ConfigTypes.ConfigLoadOptions options) {
            ConfigTypes.ConfigLoadResult result = new ConfigTypes.ConfigLoadResult();
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
                                          ConfigTypes.ConfigLoadResult result) {
            Map<String, Object> data = readYaml(configYml, logger);
            if (data == null) {
                seedDefaultMessages(result);
                return;
            }

            if (data.containsKey("debug")) {
                result.setDebug(Boolean.parseBoolean(String.valueOf(data.get("debug"))));
            }

            if (data.containsKey("behavior")) {
                String behaviorStr = data.get("behavior").toString().toLowerCase(Locale.ROOT);
                result.setBehavior(behaviorStr.startsWith("strict")
                    ? ConfigTypes.ConfigState.Behavior.STRICT
                    : ConfigTypes.ConfigState.Behavior.VANILLA);
            }

            if (data.containsKey("integrity-mode")) {
                String integrityStr = data.get("integrity-mode").toString().toLowerCase(Locale.ROOT);
                result.setIntegrityMode("dev".equals(integrityStr)
                    ? ConfigTypes.ConfigState.IntegrityMode.DEV
                    : ConfigTypes.ConfigState.IntegrityMode.SIGNED);
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

            if (data.containsKey("hash-mods")) {
                result.setHashMods(Boolean.parseBoolean(data.get("hash-mods").toString()));
            }

            if (data.containsKey("mod-versioning")) {
                result.setModVersioning(Boolean.parseBoolean(data.get("mod-versioning").toString()));
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

        private static void seedDefaultMessages(ConfigTypes.ConfigLoadResult result) {
            Map<String, String> messages = result.getMessages();
            messages.putIfAbsent("kick", DEFAULT_KICK_MESSAGE);
            messages.putIfAbsent("no-handshake", DEFAULT_NO_HANDSHAKE_MESSAGE);
            messages.putIfAbsent("missing-whitelist", DEFAULT_MISSING_WHITELIST_MESSAGE);
            messages.putIfAbsent("invalid-signature", DEFAULT_INVALID_SIGNATURE_MESSAGE);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_OUTDATED_CLIENT, DEFAULT_OUTDATED_CLIENT_MESSAGE);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_HANDSHAKE_CORRUPTED, ConfigTypes.StandardMessages.HANDSHAKE_CORRUPTED);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_HANDSHAKE_EMPTY_MOD_LIST, ConfigTypes.StandardMessages.HANDSHAKE_EMPTY_MOD_LIST);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_HANDSHAKE_HASH_MISMATCH, ConfigTypes.StandardMessages.HANDSHAKE_HASH_MISMATCH);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_HANDSHAKE_MISSING_HASH, ConfigTypes.StandardMessages.HANDSHAKE_MISSING_HASH);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_HANDSHAKE_MISSING_JAR_HASH, ConfigTypes.StandardMessages.HANDSHAKE_MISSING_JAR_HASH);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_HANDSHAKE_MISSING_NONCE, ConfigTypes.StandardMessages.HANDSHAKE_MISSING_NONCE);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_HANDSHAKE_MISSING_SIGNATURE, ConfigTypes.StandardMessages.HANDSHAKE_MISSING_SIGNATURE);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_HANDSHAKE_REPLAY, ConfigTypes.StandardMessages.HANDSHAKE_REPLAY);
            messages.putIfAbsent(ConfigTypes.StandardMessages.KEY_VELTON_FAILED, ConfigTypes.StandardMessages.VELTON_VERIFICATION_FAILED);
            result.setKickMessage(messages.get("kick"));
            result.setNoHandshakeKickMessage(messages.get("no-handshake"));
            result.setMissingWhitelistModMessage(messages.get("missing-whitelist"));
            result.setInvalidSignatureKickMessage(messages.get("invalid-signature"));
        }

        private static void loadModsYamlFiles(Path configDir,
                                              ConfigFileBootstrap.Logger logger,
                                              ConfigTypes.ConfigLoadResult result,
                                              ConfigTypes.ConfigLoadOptions options) {
            result.getModConfigMap().clear();
            result.getIgnoredMods().clear();
            result.getWhitelistedModsActive().clear();
            result.getOptionalModsActive().clear();
            result.getBlacklistedModsActive().clear();
            result.getRequiredModsActive().clear();

            List<Path> yamlFiles = listYamlFiles(configDir, logger);
            if (yamlFiles.isEmpty()) {
                return;
            }

            // Deterministic order; later files override earlier files.
            yamlFiles.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

            String defaultWhitelistedAction = options != null
                ? options.getDefaultWhitelistedAction()
                : "none";

            for (Path yamlPath : yamlFiles) {
                String filenameLower = yamlPath.getFileName().toString().toLowerCase(Locale.ROOT);
                if (filenameLower.equals("config.yml") || filenameLower.equals("mods-actions.yml")) {
                    continue;
                }

                File file = yamlPath.toFile();
                Map<String, Object> data = readYaml(file, logger);
                if (data == null || data.isEmpty()) {
                    continue;
                }

                boolean enabled = readEnabledFlag(data, true);
                if (!enabled) {
                    continue;
                }

                boolean consumed = false;
                consumed |= loadIgnoredSection(data.get("ignored"), result.getIgnoredMods(), logger, file.getName());

                consumed |= loadModeSection(
                    data.get("required"),
                    ConfigTypes.ConfigState.MODE_REQUIRED,
                    "kick",
                    result.getModConfigMap(),
                    result.getRequiredModsActive(),
                    logger,
                    file.getName()
                );

                consumed |= loadModeSection(
                    data.get("blacklisted"),
                    ConfigTypes.ConfigState.MODE_BLACKLISTED,
                    "kick",
                    result.getModConfigMap(),
                    result.getBlacklistedModsActive(),
                    logger,
                    file.getName()
                );

                // Whitelisted section is used both for whitelist-mode allowlist and for allowed-actions.
                boolean loadWhitelisted = options == null
                    || options.isLoadWhitelistedWhenDisabled()
                    || result.areModsWhitelistedEnabled();
                if (loadWhitelisted) {
                    consumed |= loadModeSection(
                        data.get("whitelisted"),
                        ConfigTypes.ConfigState.MODE_ALLOWED,
                        defaultWhitelistedAction,
                        result.getModConfigMap(),
                        result.getWhitelistedModsActive(),
                        logger,
                        file.getName()
                    );

                    consumed |= loadOptionalSection(
                        data.get("optional"),
                        result.getModConfigMap(),
                        result.getWhitelistedModsActive(),
                        result.getOptionalModsActive(),
                        defaultWhitelistedAction,
                        logger,
                        file.getName()
                    );
                }

                // Backwards compatible: if we didn't find any recognized keys in a non-empty YAML, don't warn.
                // Many users may keep extra YAML files in the config folder.
                @SuppressWarnings("unused")
                boolean ignored = consumed;
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
                                                ConfigTypes.ConfigLoadResult result,
                                                ConfigTypes.ConfigLoadOptions options) {
            result.getActionsMap().clear();

            // 1) Standard actions file
            File actionsFile = configDir.resolve("mods-actions.yml").toFile();
            Map<String, Object> data = readYaml(actionsFile, logger);
            mergeActionsFromYamlData(data, result, options);

            // 2) Embedded actions inside any enabled mods/list YAML file (e.g. mods-example.yml)
            List<Path> yamlFiles = listYamlFiles(configDir, logger);
            if (yamlFiles.isEmpty()) {
                return;
            }
            yamlFiles.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));

            for (Path yamlPath : yamlFiles) {
                String filenameLower = yamlPath.getFileName().toString().toLowerCase(Locale.ROOT);
                if (filenameLower.equals("config.yml") || filenameLower.equals("mods-actions.yml")) {
                    continue;
                }

                Map<String, Object> listData = readYaml(yamlPath.toFile(), logger);
                if (listData == null || listData.isEmpty()) {
                    continue;
                }
                if (!readEnabledFlag(listData, true)) {
                    continue;
                }
                mergeActionsFromYamlData(listData, result, options);
            }
        }

        private static void mergeActionsFromYamlData(Map<String, Object> data,
                                                     ConfigTypes.ConfigLoadResult result,
                                                     ConfigTypes.ConfigLoadOptions options) {
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
                if (entry.getKey() == null) {
                    continue;
                }
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
                        for (Object cmd : cmdList) {
                            if (cmd != null) {
                                commands.add(cmd.toString());
                            }
                        }
                    }
                }

                ConfigTypes.ActionDefinition action = new ConfigTypes.ActionDefinition(actionName, commands, shouldLog);
                boolean includeEmpty = options == null || options.isIncludeEmptyActions();
                if (includeEmpty || !action.isEmpty() || shouldLog) {
                    // last write wins
                    result.getActionsMap().put(actionName, action);
                }
            }
        }

        private static List<Path> listYamlFiles(Path configDir, ConfigFileBootstrap.Logger logger) {
            if (configDir == null) {
                return Collections.emptyList();
            }
            try (var stream = Files.list(configDir)) {
                List<Path> out = new ArrayList<>();
                stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .forEach(out::add);
                return out;
            } catch (IOException e) {
                if (logger != null) {
                    logger.warn("Failed to list YAML files in config directory: " + e.getMessage());
                }
                return Collections.emptyList();
            }
        }

        private static boolean readEnabledFlag(Map<String, Object> data, boolean defaultValue) {
            if (data == null || !data.containsKey("enabled")) {
                return defaultValue;
            }
            Object enabledObj = data.get("enabled");
            if (enabledObj instanceof Boolean) {
                return (Boolean) enabledObj;
            }
            if (enabledObj == null) {
                return defaultValue;
            }
            String s = enabledObj.toString().trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) {
                return defaultValue;
            }
            return s.equals("true") || s.equals("on") || s.equals("yes") || s.equals("1");
        }

        private static boolean loadIgnoredSection(Object ignoredObj,
                                                  Set<String> ignoredMods,
                                                  ConfigFileBootstrap.Logger logger,
                                                  String fileName) {
            if (ignoredObj == null) {
                return false;
            }

            if (ignoredObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> ignoredList = (List<Object>) ignoredObj;
                for (Object mod : ignoredList) {
                    if (mod == null) {
                        continue;
                    }
                    ConfigTypes.ModEntry entry = ConfigTypes.ModEntry.parse(mod.toString());
                    if (entry != null && entry.modId() != null) {
                        ignoredMods.add(entry.modId().toLowerCase(Locale.ROOT));
                    }
                }
                return true;
            }

            if (logger != null) {
                logger.warn("Invalid format in " + fileName + ": expected list format for ignored");
            }
            return true;
        }

        private static boolean loadModeSection(Object obj,
                                               String mode,
                                               String defaultAction,
                                               Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                               Set<String> activeSet,
                                               ConfigFileBootstrap.Logger logger,
                                               String fileName) {
            if (obj == null) {
                return false;
            }

            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    ConfigTypes.ModEntry modEntry = ConfigTypes.ModEntry.parse(entry.getKey());
                    if (modEntry == null) {
                        continue;
                    }
                    String modId = modEntry.toDisplayKey();
                    String action = entry.getValue() != null ? entry.getValue().toString() : defaultAction;
                    activeSet.add(modId);
                    modConfigMap.put(modId, new ConfigTypes.ConfigState.ModConfig(mode, action, null));
                }
                return true;
            }

            if (obj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) obj;
                for (Object mod : list) {
                    if (mod == null) {
                        continue;
                    }
                    ConfigTypes.ModEntry modEntry = ConfigTypes.ModEntry.parse(mod.toString());
                    if (modEntry == null) {
                        continue;
                    }
                    String modId = modEntry.toDisplayKey();
                    activeSet.add(modId);
                    modConfigMap.put(modId, new ConfigTypes.ConfigState.ModConfig(mode, defaultAction, null));
                }
                return true;
            }

            if (logger != null) {
                logger.warn("Invalid format in " + fileName + ": expected map or list");
            }
            return true;
        }

        private static boolean loadOptionalSection(Object obj,
                                                   Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                                   Set<String> whitelistedActive,
                                                   Set<String> optionalActive,
                                                   String defaultAction,
                                                   ConfigFileBootstrap.Logger logger,
                                                   String fileName) {
            if (obj == null) {
                return false;
            }

            if (obj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) obj;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    ConfigTypes.ModEntry modEntry = ConfigTypes.ModEntry.parse(entry.getKey());
                    if (modEntry == null) {
                        continue;
                    }
                    String modId = modEntry.toDisplayKey();
                    String action = entry.getValue() != null ? entry.getValue().toString() : defaultAction;
                    optionalActive.add(modId);
                    if (!modConfigMap.containsKey(modId) && !whitelistedActive.contains(modId)) {
                        modConfigMap.put(modId, new ConfigTypes.ConfigState.ModConfig(ConfigTypes.ConfigState.MODE_ALLOWED, action, null));
                    }
                }
                return true;
            }

            if (obj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) obj;
                for (Object mod : list) {
                    if (mod == null) {
                        continue;
                    }
                    ConfigTypes.ModEntry modEntry = ConfigTypes.ModEntry.parse(mod.toString());
                    if (modEntry == null) {
                        continue;
                    }
                    String modId = modEntry.toDisplayKey();
                    optionalActive.add(modId);
                    if (!modConfigMap.containsKey(modId) && !whitelistedActive.contains(modId)) {
                        modConfigMap.put(modId, new ConfigTypes.ConfigState.ModConfig(ConfigTypes.ConfigState.MODE_ALLOWED, defaultAction, null));
                    }
                }
                return true;
            }

            if (logger != null) {
                logger.warn("Invalid format in " + fileName + ": expected map or list for optional");
            }
            return true;
        }

        private static void createWhitelistedTemplate(File file, ConfigFileBootstrap.Logger logger) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("# Whitelisted mods which are allowed but not required,\n");
                writer.write("# but if in config.yml whitelist: true, only these mods are allowed\n");
                writer.write("# Format: modname: action (where action is from mods-actions.yml or default 'none')\n\n");
                writer.write("whitelisted:\n");
                writer.write("\n# Optional mods are allowed when whitelist is enabled but not required\n");
                writer.write("optional:\n");
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
    // endregion

    // region ConfigWriter
    public static final class ConfigWriter {
        private static final Yaml YAML = createYaml();

        private ConfigWriter() {
        }

        public static void writeAll(Path configDir,
                                    ConfigFileBootstrap.Logger logger,
                                    ConfigTypes.ConfigLoadResult data) {
            if (configDir == null || data == null) {
                return;
            }

            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                if (logger != null) {
                    logger.error("Failed to create config directory: " + configDir, e);
                }
                return;
            }

            writeConfigYml(configDir.resolve("config.yml"), logger, data);
            writeModsYamlFiles(configDir, logger, data);
        }

        private static void writeConfigYml(Path configPath,
                                           ConfigFileBootstrap.Logger logger,
                                           ConfigTypes.ConfigLoadResult data) {
            // Try to update existing file in-place to preserve order/comments.
            if (updateConfigFileInPlace(configPath, data, logger)) {
                return;
            }

            Map<String, Object> root = readYamlObject(configPath, logger);
            root.put("config", "v4");

            // Debug logging / verbosity toggle
            root.put("debug", data.isDebug());

            root.put("behavior", data.getBehavior().toString().toLowerCase(Locale.ROOT));
            root.put("integrity-mode", data.getIntegrityMode().toString().toLowerCase(Locale.ROOT));
            root.put("whitelist", data.isWhitelist());
            root.put("allow-bedrock-players", data.isAllowBedrockPlayers());
            root.put("handshake-timeout-seconds", data.getHandshakeTimeoutSeconds());
            root.put("playerdb-enabled", data.isPlayerdbEnabled());
            root.put("mods-required-enabled", data.areModsRequiredEnabled());
            root.put("mods-blacklisted-enabled", data.areModsBlacklistedEnabled());
            root.put("mods-whitelisted-enabled", data.areModsWhitelistedEnabled());
            root.put("hash-mods", data.isHashMods());
            root.put("mod-versioning", data.isModVersioning());

            Map<String, Object> messages = asObjectMap(root.get("messages"));
            if (data.getKickMessage() != null) {
                messages.put("kick", data.getKickMessage());
            }
            if (data.getNoHandshakeKickMessage() != null) {
                messages.put("no-handshake", data.getNoHandshakeKickMessage());
            }
            if (data.getMissingWhitelistModMessage() != null) {
                messages.put("missing-whitelist", data.getMissingWhitelistModMessage());
            }
            if (data.getInvalidSignatureKickMessage() != null) {
                messages.put("invalid-signature", data.getInvalidSignatureKickMessage());
            }

            for (Map.Entry<String, String> entry : data.getMessages().entrySet()) {
                if (entry.getValue() != null) {
                    messages.put(entry.getKey(), entry.getValue());
                }
            }

            root.put("messages", messages);
            writeYamlObject(configPath, root, logger, "config.yml");
        }

        private static boolean updateConfigFileInPlace(Path configPath,
                                                       ConfigTypes.ConfigLoadResult data,
                                                       ConfigFileBootstrap.Logger logger) {
            File file = configPath.toFile();
            if (!file.exists()) {
                return false;
            }

            List<String> lines;
            try {
                lines = Files.readAllLines(configPath);
            } catch (IOException e) {
                if (logger != null) {
                    logger.warn("Failed to read config.yml for in-place update: " + e.getMessage());
                }
                return false;
            }

            boolean changed = false;
            changed |= updateRootKey(lines, "config", "v4");
            changed |= updateRootKey(lines, "debug", String.valueOf(data.isDebug()));
            changed |= updateRootKey(lines, "behavior", data.getBehavior().toString().toLowerCase(Locale.ROOT));
            changed |= updateRootKey(lines, "integrity-mode", data.getIntegrityMode().toString().toLowerCase(Locale.ROOT));
            changed |= updateRootKey(lines, "whitelist", String.valueOf(data.isWhitelist()));
            changed |= updateRootKey(lines, "allow-bedrock-players", String.valueOf(data.isAllowBedrockPlayers()));
            changed |= updateRootKey(lines, "handshake-timeout-seconds", String.valueOf(data.getHandshakeTimeoutSeconds()));
            changed |= updateRootKey(lines, "playerdb-enabled", String.valueOf(data.isPlayerdbEnabled()));
            changed |= updateRootKey(lines, "mods-required-enabled", String.valueOf(data.areModsRequiredEnabled()));
            changed |= updateRootKey(lines, "mods-blacklisted-enabled", String.valueOf(data.areModsBlacklistedEnabled()));
            changed |= updateRootKey(lines, "mods-whitelisted-enabled", String.valueOf(data.areModsWhitelistedEnabled()));
            changed |= updateRootKey(lines, "hash-mods", String.valueOf(data.isHashMods()));
            changed |= updateRootKey(lines, "mod-versioning", String.valueOf(data.isModVersioning()));

            Map<String, String> messageUpdates = new LinkedHashMap<>();
            if (data.getKickMessage() != null) {
                messageUpdates.put("kick", data.getKickMessage());
            }
            if (data.getNoHandshakeKickMessage() != null) {
                messageUpdates.put("no-handshake", data.getNoHandshakeKickMessage());
            }
            if (data.getMissingWhitelistModMessage() != null) {
                messageUpdates.put("missing-whitelist", data.getMissingWhitelistModMessage());
            }
            if (data.getInvalidSignatureKickMessage() != null) {
                messageUpdates.put("invalid-signature", data.getInvalidSignatureKickMessage());
            }
            for (Map.Entry<String, String> entry : data.getMessages().entrySet()) {
                if (entry.getValue() != null) {
                    messageUpdates.put(entry.getKey(), entry.getValue());
                }
            }
            changed |= updateMessageKeys(lines, messageUpdates);

            if (!changed) {
                return true; // Nothing to change, but no need to rewrite.
            }

            try {
                Files.write(configPath, lines);
                return true;
            } catch (IOException e) {
                if (logger != null) {
                    logger.warn("Failed to update config.yml in place: " + e.getMessage());
                }
                return false;
            }
        }

        private static boolean updateRootKey(List<String> lines, String key, String value) {
            String prefix = key + ":";
            String replacement = prefix + " " + formatYamlValue(value);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }
                if (!line.startsWith(" ") && line.trim().startsWith(prefix)) {
                    lines.set(i, replacement);
                    return true;
                }
            }
            return false;
        }

        private static boolean updateMessageKeys(List<String> lines, Map<String, String> messageUpdates) {
            if (messageUpdates == null || messageUpdates.isEmpty()) {
                return false;
            }

            int messagesLine = -1;
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }
                if (!line.startsWith(" ") && line.trim().startsWith("messages:")) {
                    messagesLine = i;
                    break;
                }
            }

            if (messagesLine == -1) {
                return false;
            }

            int end = lines.size();
            for (int i = messagesLine + 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }
                if (!line.startsWith(" ") && !line.trim().isEmpty() && !line.trim().startsWith("#")) {
                    end = i;
                    break;
                }
            }

            boolean changed = false;
            for (Map.Entry<String, String> entry : messageUpdates.entrySet()) {
                String key = entry.getKey();
                String prefix = "  " + key + ":";
                String replacement = prefix + " " + formatYamlValue(entry.getValue());
                boolean updated = false;
                for (int i = messagesLine + 1; i < end; i++) {
                    String line = lines.get(i);
                    if (line == null) {
                        continue;
                    }
                    if (line.startsWith("  ") && line.trim().startsWith(key + ":")) {
                        lines.set(i, replacement);
                        updated = true;
                        changed = true;
                        break;
                    }
                }
                if (!updated) {
                    lines.add(end, replacement);
                    end++;
                    changed = true;
                }
            }
            return changed;
        }

        private static String formatYamlValue(String value) {
            if (value == null) {
                return "null";
            }

            String trimmed = value.trim();
            if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
                return trimmed.toLowerCase(Locale.ROOT);
            }

            boolean isNumber = true;
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (!Character.isDigit(c)) {
                    isNumber = false;
                    break;
                }
            }
            if (isNumber && !trimmed.isEmpty()) {
                return trimmed;
            }

            String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + escaped + "\"";
        }

        private static void writeModsYamlFiles(Path configDir,
                                               ConfigFileBootstrap.Logger logger,
                                               ConfigTypes.ConfigLoadResult data) {
            writeIgnoredMods(configDir.resolve("mods-ignored.yml"), logger, data.getIgnoredMods());
            writeModeMap(configDir.resolve("mods-required.yml"), logger, "required",
                data.getRequiredModsActive(), data.getModConfigMap(), "kick",
                "mods-required.yml");
            writeModeMap(configDir.resolve("mods-blacklisted.yml"), logger, "blacklisted",
                data.getBlacklistedModsActive(), data.getModConfigMap(), "kick",
                "mods-blacklisted.yml");
            writeWhitelistedMods(configDir.resolve("mods-whitelisted.yml"), logger,
                data.getWhitelistedModsActive(), data.getModConfigMap(), data.getOptionalModsActive());
        }

        private static void writeIgnoredMods(Path file,
                                             ConfigFileBootstrap.Logger logger,
                                             Set<String> ignoredMods) {
            Map<String, Object> root = readYamlObject(file, logger);
            if (ignoredMods == null || ignoredMods.isEmpty()) {
                // Remove the key if list is empty
                root.remove("ignored");
            } else {
                root.put("ignored", toSortedList(ignoredMods));
            }
            writeYamlObject(file, root, logger, file.getFileName().toString());
        }

        private static void writeWhitelistedMods(Path file,
                                                 ConfigFileBootstrap.Logger logger,
                                                 Set<String> whitelistedMods,
                                                 Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                                 Set<String> optionalMods) {
            Map<String, Object> root = readYamlObject(file, logger);
            
            // Separate whitelisted mods from optional mods
            Set<String> pureWhitelisted = new LinkedHashSet<>();
            if (whitelistedMods != null) {
                for (String mod : whitelistedMods) {
                    if (optionalMods == null || !optionalMods.contains(mod)) {
                        pureWhitelisted.add(mod);
                    }
                }
            }
            
            boolean hasWhitelisted = !pureWhitelisted.isEmpty();
            boolean hasOptional = optionalMods != null && !optionalMods.isEmpty();
            
            // Remove or update whitelisted section
            if (hasWhitelisted) {
                root.put("whitelisted", buildModeMap(pureWhitelisted, modConfigMap, "none"));
            } else {
                root.remove("whitelisted");
            }
            
            // Remove or update optional section
            if (hasOptional) {
                root.put("optional", buildModeMap(optionalMods, modConfigMap, "none"));
            } else {
                root.remove("optional");
            }
            
            writeYamlObject(file, root, logger, file.getFileName().toString());
        }

        private static void writeModeMap(Path file,
                                         ConfigFileBootstrap.Logger logger,
                                         String rootKey,
                                         Set<String> mods,
                                         Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                         String defaultAction,
                                         String fileName) {
            Map<String, Object> root = readYamlObject(file, logger);
            if (mods == null || mods.isEmpty()) {
                // Remove the key if list is empty
                root.remove(rootKey);
            } else {
                root.put(rootKey, buildModeMap(mods, modConfigMap, defaultAction));
            }
            writeYamlObject(file, root, logger, fileName);
        }

        private static Map<String, Object> buildModeMap(Set<String> mods,
                                                        Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                                        String defaultAction) {
            Map<String, Object> modeMap = new LinkedHashMap<>();
            for (String mod : toSortedList(mods)) {
                String action = defaultAction;
                ConfigTypes.ConfigState.ModConfig cfg = modConfigMap.get(mod.toLowerCase(Locale.ROOT));
                if (cfg != null && cfg.getActionName() != null && !cfg.getActionName().isEmpty()) {
                    action = cfg.getActionName();
                }
                modeMap.put(mod, action);
            }
            return modeMap;
        }

        private static Map<String, Object> readYamlObject(Path path,
                                                          ConfigFileBootstrap.Logger logger) {
            File file = path.toFile();
            if (!file.exists()) {
                return new LinkedHashMap<>();
            }

            try (FileReader reader = new FileReader(file)) {
                Object raw = YAML.load(reader);
                return asObjectMap(raw);
            } catch (IOException e) {
                if (logger != null) {
                    logger.error("Could not read " + file.getName(), e);
                }
                return new LinkedHashMap<>();
            }
        }

        private static void writeYamlObject(Path path,
                                            Map<String, Object> root,
                                            ConfigFileBootstrap.Logger logger,
                                            String displayName) {
            try (FileWriter writer = new FileWriter(path.toFile())) {
                YAML.dump(root, writer);
            } catch (IOException e) {
                if (logger != null) {
                    logger.error("Could not save " + displayName, e);
                }
            }
        }

        private static Map<String, Object> asObjectMap(Object value) {
            if (!(value instanceof Map<?, ?> rawMap)) {
                return new LinkedHashMap<>();
            }

            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    out.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return out;
        }

        private static List<String> toSortedList(Set<String> mods) {
            if (mods == null || mods.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> sorted = new ArrayList<>(mods);
            sorted.sort(String::compareTo);
            return sorted;
        }

        private static Yaml createYaml() {
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            options.setIndent(2);
            return new Yaml(options);
        }
    }
    // endregion
}
