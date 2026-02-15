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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
                ConfigTypes.ConfigState.MODE_REQUIRED,
                "kick",
                result.getModConfigMap(),
                result.getRequiredModsActive(),
                logger);

            File blacklistedFile = configDir.resolve("mods-blacklisted.yml").toFile();
            loadModeFile(blacklistedFile,
                "blacklisted",
                ConfigTypes.ConfigState.MODE_BLACKLISTED,
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
                    ConfigTypes.ConfigState.MODE_ALLOWED,
                    defaultWhitelistedAction,
                    result.getModConfigMap(),
                    result.getWhitelistedModsActive(),
                    logger);
                loadOptionalMods(whitelistedFile,
                    result.getModConfigMap(),
                    result.getWhitelistedModsActive(),
                    result.getOptionalModsActive(),
                    defaultWhitelistedAction,
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
                                                ConfigTypes.ConfigLoadResult result,
                                                ConfigTypes.ConfigLoadOptions options) {
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

                ConfigTypes.ActionDefinition action = new ConfigTypes.ActionDefinition(actionName, commands, shouldLog);
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
                                         Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
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
                    ConfigTypes.ModEntry modEntry = ConfigTypes.ModEntry.parse(entry.getKey());
                    if (modEntry == null) {
                        continue;
                    }
                    String modId = modEntry.toDisplayKey();
                    String action = entry.getValue() != null ? entry.getValue().toString() : defaultAction;
                    activeSet.add(modId);
                    modConfigMap.put(modId, new ConfigTypes.ConfigState.ModConfig(mode, action, null));
                }
                return;
            }

            if (obj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) obj;
                for (String mod : list) {
                    ConfigTypes.ModEntry modEntry = ConfigTypes.ModEntry.parse(mod);
                    if (modEntry == null) {
                        continue;
                    }
                    String modId = modEntry.toDisplayKey();
                    activeSet.add(modId);
                    modConfigMap.put(modId, new ConfigTypes.ConfigState.ModConfig(mode, defaultAction, null));
                }
                return;
            }

            if (logger != null) {
                logger.warn("Invalid format in " + file.getName() + ": expected map or list");
            }
        }

        private static void loadOptionalMods(File file,
                                             Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                             Set<String> whitelistedActive,
                                             Set<String> optionalActive,
                                             String defaultAction,
                                             ConfigFileBootstrap.Logger logger) {
            Map<String, Object> data = readYaml(file, logger);
            if (data == null || !data.containsKey("optional")) {
                return;
            }

            Object obj = data.get("optional");
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
                return;
            }

            if (obj instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) obj;
                for (String mod : list) {
                    ConfigTypes.ModEntry modEntry = ConfigTypes.ModEntry.parse(mod);
                    if (modEntry == null) {
                        continue;
                    }
                    String modId = modEntry.toDisplayKey();
                    optionalActive.add(modId);
                    if (!modConfigMap.containsKey(modId) && !whitelistedActive.contains(modId)) {
                        modConfigMap.put(modId, new ConfigTypes.ConfigState.ModConfig(ConfigTypes.ConfigState.MODE_ALLOWED, defaultAction, null));
                    }
                }
                return;
            }

            if (logger != null) {
                logger.warn("Invalid format in " + file.getName() + ": expected map or list for optional mods");
            }
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
            Map<String, Object> root = readYamlObject(configPath, logger);
            root.put("config", "v4");
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
                return;
            }
            root.put("ignored", toSortedList(ignoredMods));
            writeYamlObject(file, root, logger, file.getFileName().toString());
        }

        private static void writeWhitelistedMods(Path file,
                                                 ConfigFileBootstrap.Logger logger,
                                                 Set<String> whitelistedMods,
                                                 Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                                 Set<String> optionalMods) {
            Map<String, Object> root = readYamlObject(file, logger);
            boolean hasWhitelisted = whitelistedMods != null && !whitelistedMods.isEmpty();
            boolean hasOptional = optionalMods != null && !optionalMods.isEmpty();
            if (!hasWhitelisted && !hasOptional) {
                return;
            }
            if (hasWhitelisted) {
                root.put("whitelisted", buildModeMap(whitelistedMods, modConfigMap, "none"));
            }
            if (hasOptional) {
                root.put("optional", toSortedList(optionalMods));
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
            if (mods == null || mods.isEmpty()) {
                return;
            }
            Map<String, Object> root = readYamlObject(file, logger);
            root.put(rootKey, buildModeMap(mods, modConfigMap, defaultAction));
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
