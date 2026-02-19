package me.mklv.handshaker.common.configs;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


public final class ConfigLoader {
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