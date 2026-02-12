package me.mklv.handshaker.common.configs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigWriter {
    private ConfigWriter() {
    }

    public static void writeAll(Path configDir,
                                ConfigFileBootstrap.Logger logger,
                                ConfigLoadResult data) {
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
                                       ConfigLoadResult data) {
        Map<String, String> messages = new LinkedHashMap<>();
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
            messages.putIfAbsent(entry.getKey(), entry.getValue());
        }

        StringBuilder yaml = new StringBuilder();
        yaml.append("# HandShaker v4 Configuration\n");
        yaml.append("# Main plugin settings (mod-specific settings are in YAML files)\n\n");
        yaml.append("config: v4\n\n");
        yaml.append("behavior: ").append(data.getBehavior().toString().toLowerCase(Locale.ROOT)).append("\n");
        yaml.append("integrity-mode: ").append(data.getIntegrityMode().toString().toLowerCase(Locale.ROOT)).append("\n");
        yaml.append("whitelist: ").append(data.isWhitelist()).append("\n");
        yaml.append("allow-bedrock-players: ").append(data.isAllowBedrockPlayers()).append("\n");
        yaml.append("handshake-timeout-seconds: ").append(data.getHandshakeTimeoutSeconds()).append("\n");
        yaml.append("playerdb-enabled: ").append(data.isPlayerdbEnabled()).append("\n\n");
        yaml.append("mods-required-enabled: ").append(data.areModsRequiredEnabled()).append("\n");
        yaml.append("mods-blacklisted-enabled: ").append(data.areModsBlacklistedEnabled()).append("\n");
        yaml.append("mods-whitelisted-enabled: ").append(data.areModsWhitelistedEnabled()).append("\n\n");
        yaml.append("messages:\n");

        for (Map.Entry<String, String> entry : messages.entrySet()) {
            yaml.append("  ").append(entry.getKey()).append(": \"")
                .append(escapeYamlString(entry.getValue()))
                .append("\"\n");
        }

        try (FileWriter writer = new FileWriter(configPath.toFile())) {
            writer.write(yaml.toString());
        } catch (IOException e) {
            if (logger != null) {
                logger.error("Could not save config.yml", e);
            }
        }
    }

    private static void writeModsYamlFiles(Path configDir,
                                           ConfigFileBootstrap.Logger logger,
                                           ConfigLoadResult data) {
        writeIgnoredMods(configDir.resolve("mods-ignored.yml").toFile(), logger, data.getIgnoredMods());
        writeModeMap(configDir.resolve("mods-required.yml").toFile(), logger, "required",
            data.getRequiredModsActive(), data.getModConfigMap(), "kick",
            "# Required mods to join the server\n" +
            "# Format: modname: action (where action is from mods-actions.yml or default 'kick')\n\n");
        writeModeMap(configDir.resolve("mods-blacklisted.yml").toFile(), logger, "blacklisted",
            data.getBlacklistedModsActive(), data.getModConfigMap(), "kick",
            "# Blacklisted mods: modname: action\n# If a player has any of these mods, they will be kicked\n\n");
        writeModeMap(configDir.resolve("mods-whitelisted.yml").toFile(), logger, "whitelisted",
            data.getWhitelistedModsActive(), data.getModConfigMap(), "none",
            "# Whitelisted mods which are allowed but not required,\n" +
            "# but if in config.yml whitelist: true, only these mods are allowed\n" +
            "# Format: modname: action (where action is from mods-actions.yml or default 'none')\n\n");
        writeOptionalMods(configDir.resolve("mods-whitelisted.yml").toFile(), logger,
            data.getOptionalModsActive());
    }

    private static void writeOptionalMods(File file,
                                          ConfigFileBootstrap.Logger logger,
                                          Set<String> optionalMods) {
        if (optionalMods.isEmpty()) {
            return;
        }

        try (FileWriter writer = new FileWriter(file, true)) {
            StringBuilder yaml = new StringBuilder();
            yaml.append("\n# Optional mods which are allowed but not required\n");
            yaml.append("optional:\n");
            for (String mod : optionalMods) {
                yaml.append("  - ").append(mod).append("\n");
            }
            writer.write(yaml.toString());
        } catch (IOException e) {
            if (logger != null) {
                logger.error("Could not save " + file.getName(), e);
            }
        }
    }

    private static void writeIgnoredMods(File file,
                                         ConfigFileBootstrap.Logger logger,
                                         Set<String> ignoredMods) {
        if (ignoredMods.isEmpty()) {
            return;
        }

        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder yaml = new StringBuilder();
            yaml.append("# Mods which will be hidden from commands to show up\n\n");
            yaml.append("ignored:\n");
            for (String mod : ignoredMods) {
                yaml.append("  - ").append(mod).append("\n");
            }
            writer.write(yaml.toString());
        } catch (IOException e) {
            if (logger != null) {
                logger.error("Could not save " + file.getName(), e);
            }
        }
    }

    private static void writeModeMap(File file,
                                     ConfigFileBootstrap.Logger logger,
                                     String rootKey,
                                     Set<String> mods,
                                     Map<String, ConfigState.ModConfig> modConfigMap,
                                     String defaultAction,
                                     String header) {
        if (mods.isEmpty()) {
            return;
        }

        try (FileWriter writer = new FileWriter(file)) {
            StringBuilder yaml = new StringBuilder();
            yaml.append(header);
            yaml.append(rootKey).append(":\n");
            for (String mod : mods) {
                String action = defaultAction;
                ConfigState.ModConfig cfg = modConfigMap.get(mod.toLowerCase(Locale.ROOT));
                if (cfg != null && cfg.getActionName() != null && !cfg.getActionName().isEmpty()) {
                    action = cfg.getActionName();
                }
                yaml.append("  ").append(mod).append(": ").append(action).append("\n");
            }
            writer.write(yaml.toString());
        } catch (IOException e) {
            if (logger != null) {
                logger.error("Could not save " + file.getName(), e);
            }
        }
    }

    private static String escapeYamlString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
