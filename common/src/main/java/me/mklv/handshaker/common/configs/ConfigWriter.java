package me.mklv.handshaker.common.configs;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigWriter {
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
            writeMessagesYml(configDir.resolve("messages.yml"), logger, data);
            writeModsYamlFiles(configDir, logger, data);
        }

        private static void writeConfigYml(Path configPath,
                                           ConfigFileBootstrap.Logger logger,
                                           ConfigTypes.ConfigLoadResult data) {
            Map<String, Object> root = readYamlObject(configPath, logger);
            Map<String, Object> settings = new LinkedHashMap<>();
            settings.put("config-version", 7);
            settings.put("debug", data.isDebug());
            settings.put("force-handshaker-mod", data.isForceHandshakerMod());
            settings.put("allow-bedrock-players", data.isAllowBedrockPlayers());
            settings.put("handshake-timeout-seconds", data.getHandshakeTimeoutSeconds());
            settings.put("enforce-whitelisted-mod-list", data.isWhitelist());
            settings.put("mod-versioning", data.isModVersioning());
            settings.put("required-modpack-hashes", new ArrayList<>(data.getRequiredModpackHashes()));

            Map<String, Object> compatibility = new LinkedHashMap<>();
            compatibility.put("modern-7.0+", data.isModernCompatibility());
            compatibility.put("hybrid-6.0", data.isHybridCompatibility());
            compatibility.put("legacy-3.0+", data.isLegacyCompatibility());
            compatibility.put("unsigned", data.isUnsignedCompatibility());
            settings.put("handshaker-version-compatibility", compatibility);

            Map<String, Object> playerDatabase = new LinkedHashMap<>();
            playerDatabase.put("enabled", data.isPlayerdbEnabled());
            playerDatabase.put("use-hash-for-mods", data.isHashMods());
            playerDatabase.put("runtime-cache", data.isRuntimeCache());
            settings.put("player-database", playerDatabase);

            Map<String, Object> customLists = new LinkedHashMap<>();
            customLists.put("mods-required-enabled", data.areModsRequiredEnabled());
            customLists.put("mods-blacklisted-enabled", data.areModsBlacklistedEnabled());
            customLists.put("mods-whitelisted-enabled", data.areModsWhitelistedEnabled());
            settings.put("custom-lists", customLists);

            settings.put("default-action", data.getDefaultAction() != null ? data.getDefaultAction() : "kick");
            root.put("settings", settings);
            root.remove("config");
            root.remove("debug");
            root.remove("behavior");
            root.remove("integrity-mode");
            root.remove("whitelist");
            root.remove("playerdb-enabled");
            root.remove("mods-required-enabled");
            root.remove("mods-blacklisted-enabled");
            root.remove("mods-whitelisted-enabled");
            root.remove("hash-mods");
            root.remove("Runtime_cache");
            root.remove("runtime_cache");

            root.remove("messages");
            writeYamlObject(configPath, root, logger, "config.yml");
            rewriteRequiredModpackHashesInline(configPath, data.getRequiredModpackHashes(), logger);
        }

        private static void writeMessagesYml(Path messagesPath,
                                             ConfigFileBootstrap.Logger logger,
                                             ConfigTypes.ConfigLoadResult data) {
            Map<String, Object> root = readYamlObject(messagesPath, logger);
            Map<String, Object> messages = new LinkedHashMap<>();

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
            // Write ban message default if not already configured in messages.yml
            if (!data.getMessages().containsKey(ConfigTypes.StandardMessages.KEY_BAN)) {
                messages.put(ConfigTypes.StandardMessages.KEY_BAN, ConfigTypes.StandardMessages.DEFAULT_BAN_MESSAGE);
            }

            for (Map.Entry<String, String> entry : data.getMessages().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    messages.put(entry.getKey(), entry.getValue());
                }
            }

            root.put("messages", messages);
            writeYamlObject(messagesPath, root, logger, "messages.yml");
        }

        // private static boolean updateConfigFileInPlace(Path configPath,
        //                                                ConfigTypes.ConfigLoadResult data,
        //                                                ConfigFileBootstrap.Logger logger) {
        //     File file = configPath.toFile();
        //     if (!file.exists()) {
        //         return false;
        //     }

        //     List<String> lines;
        //     try {
        //         lines = Files.readAllLines(configPath);
        //     } catch (IOException e) {
        //         if (logger != null) {
        //             logger.warn("Failed to read config.yml for in-place update: " + e.getMessage());
        //         }
        //         return false;
        //     }

        //     boolean changed = false;
        //     changed |= updateRootKey(lines, "config", "v5");
        //     changed |= updateRootKey(lines, "debug", String.valueOf(data.isDebug()));
        //     changed |= updateRootKey(lines, "behavior", data.getBehavior().toString().toLowerCase(Locale.ROOT));
        //     changed |= updateRootKey(lines, "integrity-mode", data.getIntegrityMode().toString().toLowerCase(Locale.ROOT));
        //     changed |= updateRootKey(lines, "whitelist", String.valueOf(data.isWhitelist()));
        //     changed |= updateRootKey(lines, "allow-bedrock-players", String.valueOf(data.isAllowBedrockPlayers()));
        //     changed |= updateRootKey(lines, "handshake-timeout-seconds", String.valueOf(data.getHandshakeTimeoutSeconds()));
        //     changed |= updateRootKey(lines, "playerdb-enabled", String.valueOf(data.isPlayerdbEnabled()));
        //     changed |= updateRootKey(lines, "mods-required-enabled", String.valueOf(data.areModsRequiredEnabled()));
        //     changed |= updateRootKey(lines, "mods-blacklisted-enabled", String.valueOf(data.areModsBlacklistedEnabled()));
        //     changed |= updateRootKey(lines, "mods-whitelisted-enabled", String.valueOf(data.areModsWhitelistedEnabled()));
        //     changed |= updateRootKey(lines, "hash-mods", String.valueOf(data.isHashMods()));
        //     changed |= updateRootKey(lines, "Runtime_cache", String.valueOf(data.isRuntimeCache()));
        //     changed |= updateRootKey(lines, "mod-versioning", String.valueOf(data.isModVersioning()));
        //     String requiredModpackHashValue = data.getRequiredModpackHash() != null ? data.getRequiredModpackHash() : "off";
        //     boolean updatedRequiredHash = updateRootKey(lines, "required-modpack-hash", requiredModpackHashValue);
        //     if (!updatedRequiredHash) {
        //         lines.add("required-modpack-hash: " + formatYamlValue(requiredModpackHashValue));
        //         changed = true;
        //     } else {
        //         changed = true;
        //     }
        //     String defaultActionValue = data.getDefaultAction() != null ? data.getDefaultAction() : "kick";
        //     if (!updateRootKey(lines, "default-action", defaultActionValue)) {
        //         lines.add("default-action: " + formatYamlValue(defaultActionValue));
        //     }
        //     changed = true;

        //     Map<String, String> messageUpdates = new LinkedHashMap<>();
        //     if (data.getKickMessage() != null) {
        //         messageUpdates.put("kick", data.getKickMessage());
        //     }
        //     if (data.getNoHandshakeKickMessage() != null) {
        //         messageUpdates.put("no-handshake", data.getNoHandshakeKickMessage());
        //     }
        //     if (data.getMissingWhitelistModMessage() != null) {
        //         messageUpdates.put("missing-whitelist", data.getMissingWhitelistModMessage());
        //     }
        //     if (data.getInvalidSignatureKickMessage() != null) {
        //         messageUpdates.put("invalid-signature", data.getInvalidSignatureKickMessage());
        //     }
        //     for (Map.Entry<String, String> entry : data.getMessages().entrySet()) {
        //         if (entry.getValue() != null) {
        //             messageUpdates.put(entry.getKey(), entry.getValue());
        //         }
        //     }
        //     changed |= updateMessageKeys(lines, messageUpdates);

        //     if (!changed) {
        //         return true; // Nothing to change, but no need to rewrite.
        //     }

        //     try {
        //         Files.write(configPath, lines);
        //         return true;
        //     } catch (IOException e) {
        //         if (logger != null) {
        //             logger.warn("Failed to update config.yml in place: " + e.getMessage());
        //         }
        //         return false;
        //     }
        // }

        // private static boolean updateRootKey(List<String> lines, String key, String value) {
        //     String prefix = key + ":";
        //     String replacement = prefix + " " + formatYamlValue(value);
        //     for (int i = 0; i < lines.size(); i++) {
        //         String line = lines.get(i);
        //         if (line == null) {
        //             continue;
        //         }
        //         if (!line.startsWith(" ") && line.trim().startsWith(prefix)) {
        //             lines.set(i, replacement);
        //             return true;
        //         }
        //     }
        //     return false;
        // }

        // private static boolean updateMessageKeys(List<String> lines, Map<String, String> messageUpdates) {
        //     if (messageUpdates == null || messageUpdates.isEmpty()) {
        //         return false;
        //     }

        //     int messagesLine = -1;
        //     for (int i = 0; i < lines.size(); i++) {
        //         String line = lines.get(i);
        //         if (line == null) {
        //             continue;
        //         }
        //         if (!line.startsWith(" ") && line.trim().startsWith("messages:")) {
        //             messagesLine = i;
        //             break;
        //         }
        //     }

        //     if (messagesLine == -1) {
        //         return false;
        //     }

        //     int end = lines.size();
        //     for (int i = messagesLine + 1; i < lines.size(); i++) {
        //         String line = lines.get(i);
        //         if (line == null) {
        //             continue;
        //         }
        //         if (!line.startsWith(" ") && !line.trim().isEmpty() && !line.trim().startsWith("#")) {
        //             end = i;
        //             break;
        //         }
        //     }

        //     boolean changed = false;
        //     for (Map.Entry<String, String> entry : messageUpdates.entrySet()) {
        //         String key = entry.getKey();
        //         String prefix = "  " + key + ":";
        //         String replacement = prefix + " " + formatYamlValue(entry.getValue());
        //         boolean updated = false;
        //         for (int i = messagesLine + 1; i < end; i++) {
        //             String line = lines.get(i);
        //             if (line == null) {
        //                 continue;
        //             }
        //             if (line.startsWith("  ") && line.trim().startsWith(key + ":")) {
        //                 lines.set(i, replacement);
        //                 updated = true;
        //                 changed = true;
        //                 break;
        //             }
        //         }
        //         if (!updated) {
        //             lines.add(end, replacement);
        //             end++;
        //             changed = true;
        //         }
        //     }
        //     return changed;
        // }

        // private static String formatYamlValue(String value) {
        //     if (value == null) {
        //         return "null";
        //     }

        //     String trimmed = value.trim();
        //     if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
        //         return trimmed.toLowerCase(Locale.ROOT);
        //     }

        //     boolean isNumber = true;
        //     for (int i = 0; i < trimmed.length(); i++) {
        //         char c = trimmed.charAt(i);
        //         if (!Character.isDigit(c)) {
        //             isNumber = false;
        //             break;
        //         }
        //     }
        //     if (isNumber && !trimmed.isEmpty()) {
        //         return trimmed;
        //     }

        //     String escaped = trimmed.replace("\\", "\\\\").replace("\"", "\\\"");
        //     return "\"" + escaped + "\"";
        // }

        private static void writeModsYamlFiles(Path configDir,
                                               ConfigFileBootstrap.Logger logger,
                                               ConfigTypes.ConfigLoadResult data) {
            Path modListsDir = configDir.resolve("mod-lists");
            try {
                Files.createDirectories(modListsDir);
            } catch (IOException e) {
                if (logger != null) {
                    logger.error("Could not create mod-lists directory", e);
                }
                return;
            }

            writeIgnoredMods(modListsDir.resolve("mods-ignored.yml"), logger, data.getIgnoredMods());
            writeModeMap(modListsDir.resolve("mods-required.yml"), logger, "required",
                data.getRequiredModsActive(), data.getModConfigMap(), "kick",
                "mods-required.yml");
            writeModeMap(modListsDir.resolve("mods-blacklisted.yml"), logger, "blacklisted",
                data.getBlacklistedModsActive(), data.getModConfigMap(), "kick",
                "mods-blacklisted.yml");
            writeWhitelistedMods(modListsDir.resolve("mods-whitelisted.yml"), logger,
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
            } catch (RuntimeException e) {
                if (logger != null) {
                    logger.warn("Could not parse " + file.getName() + ", rewriting with defaults: " + e.getMessage());
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

        private static void rewriteRequiredModpackHashesInline(Path configPath,
                                                               Set<String> requiredModpackHashes,
                                                               ConfigFileBootstrap.Logger logger) {
            try {
                List<String> lines = Files.readAllLines(configPath);
                List<String> rewritten = new ArrayList<>();

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("required-modpack-hashes:")) {
                        String indent = leadingWhitespace(line);
                        int keyIndent = indent.length();
                        rewritten.add(indent + "required-modpack-hashes: " + formatInlineYamlList(requiredModpackHashes));

                        while (i + 1 < lines.size()) {
                            String next = lines.get(i + 1);
                            String nextTrimmed = next.trim();
                            int nextIndent = leadingWhitespace(next).length();

                            if (nextTrimmed.isEmpty()) {
                                break;
                            }
                            if (nextTrimmed.startsWith("#")) {
                                break;
                            }
                            if (nextTrimmed.equals("]") || nextTrimmed.equals("[")) {
                                i++;
                                continue;
                            }
                            if (nextTrimmed.startsWith("- ")) {
                                i++;
                                continue;
                            }
                            if (nextIndent > keyIndent) {
                                i++;
                                continue;
                            }
                            break;
                        }
                        continue;
                    }
                    rewritten.add(line);
                }

                Files.write(configPath, rewritten);
            } catch (IOException e) {
                if (logger != null) {
                    logger.warn("Failed to rewrite required-modpack-hashes inline: " + e.getMessage());
                }
            }
        }

        private static String formatInlineYamlList(Set<String> values) {
            if (values == null || values.isEmpty()) {
                return "[]";
            }
            return "[" + String.join(", ", toSortedList(values)) + "]";
        }

        private static String leadingWhitespace(String value) {
            int index = 0;
            while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
                index++;
            }
            return value.substring(0, index);
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

