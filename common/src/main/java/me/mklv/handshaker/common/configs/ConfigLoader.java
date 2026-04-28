package me.mklv.handshaker.common.configs;

import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
            loadMessagesYml(configDir, logger, result);

            loadModsYamlFiles(configDir, logger, result, options);
            loadActionsYamlFile(configDir, logger, result, options);

            return result;
        }

        private static void ensureDefaults(Path configDir,
                                           Class<?> resourceBase,
                                           ConfigFileBootstrap.Logger logger) {
            ConfigFileBootstrap.copyRequired(configDir, "config.yml", resourceBase, logger);
            String[] modsFiles = {
                "mod-lists/mods-required.yml",
                "mod-lists/mods-blacklisted.yml",
                "mod-lists/mods-whitelisted.yml",
                "mod-lists/mods-ignored.yml",
                "mods-actions.yml"
            };
            for (String filename : modsFiles) {
                ConfigFileBootstrap.copyOptional(configDir, filename, resourceBase, logger);
            }
            ConfigFileBootstrap.copyOptional(configDir, "messages.yml", resourceBase, logger);
        }

        private static void loadConfigYml(File configYml,
                                          ConfigFileBootstrap.Logger logger,
                                          ConfigTypes.ConfigLoadResult result) {
            Map<String, Object> data = readYaml(configYml, logger);
            if (data == null) {
                seedDefaultMessages(result);
                return;
            }
            Map<String, Object> settings = asMap(data.get("settings"));
            boolean hasSettings = settings != null && !settings.isEmpty();
            Map<String, Object> root = hasSettings ? settings : data;

            if (!hasSettings) {
                result.setRewriteToV7(true);
            }

            Integer configVersion = readInteger(root.get("config-version"));
            if (configVersion == null) {
                String legacyConfigTag = readString(data.get("config"));
                if (legacyConfigTag != null && legacyConfigTag.toLowerCase(Locale.ROOT).startsWith("v")) {
                    configVersion = readInteger(legacyConfigTag.substring(1));
                }
            }
            if (configVersion == null || configVersion < 7) {
                result.setRewriteToV7(true);
            }

            if (root.containsKey("debug")) {
                result.setDebug(readBoolean(root.get("debug"), false));
            }

            if (root.containsKey("handshaker-enforcement")) {
                result.setForceHandshakerMod(readBoolean(root.get("handshaker-enforcement"), true));
            } else if (root.containsKey("force-handshaker-mod")) {
                result.setForceHandshakerMod(readBoolean(root.get("force-handshaker-mod"), true));
                result.setRewriteToV7(true);
            }

            Map<String, Object> compatibility = asMap(root.get("handshaker-version-compatibility"));
            if (compatibility != null && !compatibility.isEmpty()) {
                result.setModernCompatibility(readBoolean(compatibility.get("modern-7.0+"), true));
                result.setHybridCompatibility(readBoolean(compatibility.get("hybrid-6.0"), false));
                result.setLegacyCompatibility(readBoolean(compatibility.get("legacy-3.0+"), false));
                result.setUnsignedCompatibility(readBoolean(compatibility.get("unsigned"), false));
            }

            if (root.containsKey("whitelist-enforcement")) {
                result.setWhitelist(readBoolean(root.get("whitelist-enforcement"), false));
            } else if (root.containsKey("enforce-whitelisted-mod-list")) {
                result.setWhitelist(readBoolean(root.get("enforce-whitelisted-mod-list"), false));
                result.setRewriteToV7(true);
            } else if (root.containsKey("whitelist")) {
                result.setWhitelist(readBoolean(root.get("whitelist"), false));
                result.setRewriteToV7(true);
            }

            if (root.containsKey("bedrock-policy")) {
                result.setAllowBedrockPlayers(readBoolean(root.get("bedrock-policy"), false));
            } else if (root.containsKey("allow-bedrock-players")) {
                result.setAllowBedrockPlayers(readBoolean(root.get("allow-bedrock-players"), false));
                result.setRewriteToV7(true);
            }

            if (root.containsKey("timeout-seconds")) {
                Integer timeout = readInteger(root.get("timeout-seconds"));
                result.setHandshakeTimeoutSeconds(timeout != null ? Math.max(1, timeout) : 5);
            } else if (root.containsKey("handshake-timeout-seconds")) {
                Integer timeout = readInteger(root.get("handshake-timeout-seconds"));
                result.setHandshakeTimeoutSeconds(timeout != null ? Math.max(1, timeout) : 5);
                result.setRewriteToV7(true);
            }

            if (root.containsKey("database")) {
                result.setPlayerdbEnabled(readBoolean(root.get("database"), false));
                result.setHashMods(readBoolean(root.get("use-hash-for-mods"), true));
                result.setRuntimeCache(readBoolean(root.get("runtime-cache"), false));
            } else {
                Map<String, Object> playerDatabase = asMap(root.get("player-database"));
                if (playerDatabase != null && !playerDatabase.isEmpty()) {
                    result.setPlayerdbEnabled(readBoolean(playerDatabase.get("enabled"), false));
                    result.setHashMods(readBoolean(playerDatabase.get("use-hash-for-mods"), true));
                    result.setRuntimeCache(readBoolean(playerDatabase.get("runtime-cache"), false));
                    result.setRewriteToV7(true);
                } else {
                    if (root.containsKey("playerdb-enabled")) {
                        result.setPlayerdbEnabled(readBoolean(root.get("playerdb-enabled"), false));
                        result.setRewriteToV7(true);
                    }
                    if (root.containsKey("hash-mods")) {
                        result.setHashMods(readBoolean(root.get("hash-mods"), true));
                        result.setRewriteToV7(true);
                    }
                    if (root.containsKey("Runtime_cache")) {
                        result.setRuntimeCache(readBoolean(root.get("Runtime_cache"), false));
                        result.setRewriteToV7(true);
                    } else if (root.containsKey("runtime_cache")) {
                        result.setRuntimeCache(readBoolean(root.get("runtime_cache"), false));
                        result.setRewriteToV7(true);
                    }
                }
            }

            Map<String, Object> customLists = asMap(root.get("custom-lists"));
            if (customLists != null && !customLists.isEmpty()) {
                result.setModsRequiredEnabled(readBoolean(customLists.get("mods-required-enabled"), true));
                result.setModsBlacklistedEnabled(readBoolean(customLists.get("mods-blacklisted-enabled"), true));
                result.setModsWhitelistedEnabled(readBoolean(customLists.get("mods-whitelisted-enabled"), true));
            } else {
                if (root.containsKey("mods-required-enabled")) {
                    result.setModsRequiredEnabled(readBoolean(root.get("mods-required-enabled"), true));
                    result.setRewriteToV7(true);
                }
                if (root.containsKey("mods-blacklisted-enabled")) {
                    result.setModsBlacklistedEnabled(readBoolean(root.get("mods-blacklisted-enabled"), true));
                    result.setRewriteToV7(true);
                }
                if (root.containsKey("mods-whitelisted-enabled")) {
                    result.setModsWhitelistedEnabled(readBoolean(root.get("mods-whitelisted-enabled"), true));
                    result.setRewriteToV7(true);
                }
            }

            if (root.containsKey("mod-versioning")) {
                result.setModVersioning(readBoolean(root.get("mod-versioning"), true));
            }

            if (root.containsKey("modpack-hashes")) {
                result.setRequiredModpackHashes(parseRequiredModpackHashes(root.get("modpack-hashes")));
            } else if (root.containsKey("required-modpack-hashes")) {
                result.setRequiredModpackHashes(parseRequiredModpackHashes(root.get("required-modpack-hashes")));
                result.setRewriteToV7(true);
            } else if (root.containsKey("required-modpack-hash")) {
                result.setRequiredModpackHashes(parseRequiredModpackHashes(root.get("required-modpack-hash")));
                result.setRewriteToV7(true);
            }

            if (root.containsKey("default-action")) {
                String defaultAction = String.valueOf(root.get("default-action")).trim().toLowerCase(Locale.ROOT);
                if (!defaultAction.isEmpty()) {
                    result.setDefaultAction(defaultAction);
                }
            }

            Map<String, Object> advanced = asMap(data.get("advanced-experimental"));
            if (advanced != null && !advanced.isEmpty()) {
                Integer rateLimit = readInteger(advanced.get("rate-limit-per-minute"));
                if (rateLimit != null) {
                    result.setRateLimitPerMinute(rateLimit);
                }
                if (advanced.containsKey("enable-diagnostic-command")) {
                    result.setDiagnosticCommandEnabled(readBoolean(advanced.get("enable-diagnostic-command"), true));
                }
                Map<String, Object> database = asMap(advanced.get("database"));
                if (database != null && !database.isEmpty()) {
                    if (database.containsKey("async-operations")) {
                        result.setAsyncDatabaseOperations(readBoolean(database.get("async-operations"), true));
                    }
                    Integer pool = readInteger(database.get("pool-size"));
                    if (pool != null) {
                        result.setDatabasePoolSize(pool);
                    }
                    Integer idleMs = readInteger(database.get("idle-timeout-ms"));
                    if (idleMs != null) {
                        result.setDatabaseIdleTimeoutMs(idleMs);
                    }
                    Integer maxLifeMs = readInteger(database.get("max-lifetime-ms"));
                    if (maxLifeMs != null) {
                        result.setDatabaseMaxLifetimeMs(maxLifeMs);
                    }
                    Integer deleteHistoryDays = readInteger(database.get("delete-history-days"));
                    if (deleteHistoryDays != null) {
                        result.setDeleteHistoryDays(deleteHistoryDays);
                    }
                }

                Map<String, Object> payload = asMap(advanced.get("payload"));
                if (payload != null && payload.containsKey("compression-enabled")) {
                    result.setPayloadCompressionEnabled(readBoolean(payload.get("compression-enabled"), true));
                }

                Map<String, Object> api = asMap(advanced.get("api"));
                if (api != null && !api.isEmpty()) {
                    if (api.containsKey("enable-rest-api")) {
                        result.setRestApiEnabled(readBoolean(api.get("enable-rest-api"), false));
                    }
                    Integer restApiPort = readInteger(api.get("rest-api-port"));
                    if (restApiPort != null) {
                        result.setRestApiPort(restApiPort);
                    }
                    String apiKey = readString(api.get("api-key"));
                    if (apiKey != null && !apiKey.isEmpty()) {
                        result.setRestApiKey(apiKey);
                    }
                }
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

        private static void loadMessagesYml(Path configDir,
                                            ConfigFileBootstrap.Logger logger,
                                            ConfigTypes.ConfigLoadResult result) {
            File messagesYml = configDir.resolve("messages.yml").toFile();
            Map<String, Object> messagesData = readYaml(messagesYml, logger);
            if (messagesData == null || messagesData.isEmpty()) {
                return;
            }

            Map<String, Object> messages = asMap(messagesData.get("messages"));
            if (messages == null || messages.isEmpty()) {
                return;
            }

            for (Map.Entry<String, Object> entry : messages.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result.getMessages().put(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }

            result.setKickMessage(result.getMessages().getOrDefault("kick", DEFAULT_KICK_MESSAGE));
            result.setNoHandshakeKickMessage(result.getMessages().getOrDefault("no-handshake", DEFAULT_NO_HANDSHAKE_MESSAGE));
            result.setMissingWhitelistModMessage(result.getMessages().getOrDefault("missing-whitelist", DEFAULT_MISSING_WHITELIST_MESSAGE));
            result.setInvalidSignatureKickMessage(result.getMessages().getOrDefault("invalid-signature", DEFAULT_INVALID_SIGNATURE_MESSAGE));
        }

        private static void seedDefaultMessages(ConfigTypes.ConfigLoadResult result) {
            ConfigLoaderSupport.seedDefaultMessages(
                result,
                DEFAULT_KICK_MESSAGE,
                DEFAULT_NO_HANDSHAKE_MESSAGE,
                DEFAULT_MISSING_WHITELIST_MESSAGE,
                DEFAULT_INVALID_SIGNATURE_MESSAGE,
                DEFAULT_OUTDATED_CLIENT_MESSAGE
            );
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
                if (filenameLower.equals("config.yml") || filenameLower.equals("mods-actions.yml") || filenameLower.equals("messages.yml")) {
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
                File whitelistedFile = configDir.resolve("mod-lists").resolve("mods-whitelisted.yml").toFile();
                if (!whitelistedFile.exists()) {
                    File parent = whitelistedFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
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
                if (filenameLower.equals("config.yml") || filenameLower.equals("mods-actions.yml") || filenameLower.equals("messages.yml")) {
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
            return ConfigLoaderSupport.listYamlFiles(configDir, logger);
        }

        private static boolean readEnabledFlag(Map<String, Object> data, boolean defaultValue) {
            return ConfigLoaderSupport.readEnabledFlag(data, defaultValue);
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

        private static Set<String> parseRequiredModpackHashes(Object value) {
            Set<String> hashes = new LinkedHashSet<>();
            if (value == null) {
                return hashes;
            }

            if (value instanceof List<?> list) {
                for (Object entry : list) {
                    String normalized = normalizeRequiredModpackHash(entry);
                    if (normalized != null) {
                        hashes.add(normalized);
                    }
                }
                return hashes;
            }

            String normalized = normalizeRequiredModpackHash(value);
            if (normalized != null) {
                hashes.add(normalized);
            }
            return hashes;
        }

        private static String normalizeRequiredModpackHash(Object value) {
            if (value == null) {
                return null;
            }

            String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()
                || "off".equals(normalized)
                || "none".equals(normalized)
                || "null".equals(normalized)
                || "false".equals(normalized)) {
                return null;
            }

            return normalized.matches("[0-9a-f]{64}") ? normalized : null;
        }

        private static void createWhitelistedTemplate(File file, ConfigFileBootstrap.Logger logger) {
            ConfigLoaderSupport.createWhitelistedTemplate(file, logger);
        }

        private static Map<String, Object> readYaml(File file, ConfigFileBootstrap.Logger logger) {
            return ConfigLoaderSupport.readYaml(file, logger);
        }

        private static Map<String, Object> asMap(Object value) {
            return ConfigLoaderSupport.asMap(value);
        }

        private static boolean readBoolean(Object value, boolean fallback) {
            return ConfigLoaderSupport.readBoolean(value, fallback);
        }

        private static Integer readInteger(Object value) {
            return ConfigLoaderSupport.readInteger(value);
        }

        private static String readString(Object value) {
            return ConfigLoaderSupport.readString(value);
        }
    }