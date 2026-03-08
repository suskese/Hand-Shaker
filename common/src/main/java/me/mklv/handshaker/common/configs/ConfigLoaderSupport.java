package me.mklv.handshaker.common.configs;

import org.yaml.snakeyaml.Yaml;
import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ConfigLoaderSupport {
    private ConfigLoaderSupport() {
    }

    static void seedDefaultMessages(ConfigTypes.ConfigLoadResult result,
                                    String defaultKickMessage,
                                    String defaultNoHandshakeMessage,
                                    String defaultMissingWhitelistMessage,
                                    String defaultInvalidSignatureMessage,
                                    String defaultOutdatedClientMessage) {
        Map<String, String> messages = result.getMessages();
        messages.putIfAbsent("kick", defaultKickMessage);
        messages.putIfAbsent("no-handshake", defaultNoHandshakeMessage);
        messages.putIfAbsent("missing-whitelist", defaultMissingWhitelistMessage);
        messages.putIfAbsent("invalid-signature", defaultInvalidSignatureMessage);
        messages.putIfAbsent(StandardMessages.KEY_OUTDATED_CLIENT, defaultOutdatedClientMessage);
        messages.putIfAbsent(StandardMessages.KEY_HANDSHAKE_CORRUPTED, StandardMessages.HANDSHAKE_CORRUPTED);
        messages.putIfAbsent(StandardMessages.KEY_HANDSHAKE_EMPTY_MOD_LIST, StandardMessages.HANDSHAKE_EMPTY_MOD_LIST);
        messages.putIfAbsent(StandardMessages.KEY_HANDSHAKE_HASH_MISMATCH, StandardMessages.HANDSHAKE_HASH_MISMATCH);
        messages.putIfAbsent(StandardMessages.KEY_HANDSHAKE_MISSING_HASH, StandardMessages.HANDSHAKE_MISSING_HASH);
        messages.putIfAbsent(StandardMessages.KEY_HANDSHAKE_MISSING_JAR_HASH, StandardMessages.HANDSHAKE_MISSING_JAR_HASH);
        messages.putIfAbsent(StandardMessages.KEY_HANDSHAKE_MISSING_NONCE, StandardMessages.HANDSHAKE_MISSING_NONCE);
        messages.putIfAbsent(StandardMessages.KEY_HANDSHAKE_MISSING_SIGNATURE, StandardMessages.HANDSHAKE_MISSING_SIGNATURE);
        messages.putIfAbsent(StandardMessages.KEY_HANDSHAKE_REPLAY, StandardMessages.HANDSHAKE_REPLAY);
        messages.putIfAbsent(StandardMessages.KEY_VELTON_FAILED, StandardMessages.VELTON_VERIFICATION_FAILED);
        messages.putIfAbsent(StandardMessages.KEY_MODPACK_HASH_MISMATCH, StandardMessages.MODPACK_HASH_MISMATCH);
        result.setKickMessage(messages.get("kick"));
        result.setNoHandshakeKickMessage(messages.get("no-handshake"));
        result.setMissingWhitelistModMessage(messages.get("missing-whitelist"));
        result.setInvalidSignatureKickMessage(messages.get("invalid-signature"));
    }

    static List<Path> listYamlFiles(Path configDir, ConfigFileBootstrap.Logger logger) {
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

    static boolean readEnabledFlag(Map<String, Object> data, boolean defaultValue) {
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

    static void createWhitelistedTemplate(File file, ConfigFileBootstrap.Logger logger) {
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

    static Map<String, Object> readYaml(File file, ConfigFileBootstrap.Logger logger) {
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

    static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                map.put(entry.getKey().toString(), entry.getValue());
            }
        }
        return map;
    }

    static boolean readBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return fallback;
        }
        return text.equals("true") || text.equals("on") || text.equals("yes") || text.equals("1");
    }

    static Integer readInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static String readString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
