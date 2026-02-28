package me.mklv.handshaker.common.commands;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import me.mklv.handshaker.common.configs.ConfigFileBootstrap;
import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;

public final class ConfigCommandOperations {
    private ConfigCommandOperations() {
    }

    public record MutationResult(
        boolean success,
        String message,
        boolean shouldSave,
        boolean shouldReloadConfig,
        boolean shouldRecheckPlayers
    ) {
    }

    public static MutationResult applyConfigValue(
        CommonConfigManagerBase config,
        String param,
        String value,
        Set<String> currentPlayerMods
    ) {
        if (config == null) {
            return new MutationResult(false, "Config manager not available", false, false, false);
        }

        String key = param == null ? "" : param.toLowerCase(Locale.ROOT);
        String input = value == null ? "" : value.trim();

        switch (key) {
            case "behavior" -> {
                if (!input.equalsIgnoreCase("STRICT") && !input.equalsIgnoreCase("VANILLA")) {
                    return new MutationResult(false, "Behavior must be STRICT or VANILLA", false, false, false);
                }
                config.setBehavior(input);
                return new MutationResult(true, "Behavior set to " + input.toUpperCase(Locale.ROOT), true, false, true);
            }
            case "integrity" -> {
                if (!input.equalsIgnoreCase("SIGNED") && !input.equalsIgnoreCase("DEV")) {
                    return new MutationResult(false, "Integrity must be SIGNED or DEV", false, false, false);
                }
                config.setIntegrityMode(input);
                return new MutationResult(true, "Integrity mode set to " + input.toUpperCase(Locale.ROOT), true, false, false);
            }
            case "whitelist" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "Whitelist must be true/false or on/off", false, false, false);
                }
                config.setWhitelist(enabled);
                return new MutationResult(true, "Whitelist mode " + (enabled ? "ON" : "OFF"), true, false, true);
            }
            case "allow_bedrock" -> {
                Boolean allow = CommandHelper.parseEnableFlag(input);
                if (allow == null) {
                    return new MutationResult(false, "allow_bedrock must be true/false or on/off", false, false, false);
                }
                config.setAllowBedrockPlayers(allow);
                return new MutationResult(true, "Bedrock players " + (allow ? "allowed" : "blocked"), true, false, true);
            }
            case "playerdb_enabled" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "playerdb_enabled must be true/false or on/off", false, false, false);
                }
                config.setPlayerdbEnabled(enabled);
                return new MutationResult(true, "Player database " + (enabled ? "enabled" : "disabled"), true, false, false);
            }
            case "hash_mods" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "hash_mods must be true/false or on/off", false, false, false);
                }
                config.setHashMods(enabled);
                return new MutationResult(true, "hash_mods " + (enabled ? "enabled" : "disabled"), true, false, false);
            }
            case "mod_versioning" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "mod_versioning must be true/false or on/off", false, false, false);
                }
                config.setModVersioning(enabled);
                return new MutationResult(true, "mod_versioning " + (enabled ? "enabled" : "disabled"), true, false, false);
            }
            case "runtime_cache" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "runtime_cache must be true/false or on/off", false, false, false);
                }
                config.setRuntimeCache(enabled);
                return new MutationResult(true, "runtime_cache " + (enabled ? "enabled" : "disabled"), true, false, false);
            }
            case "handshake_timeout" -> {
                try {
                    int seconds = Integer.parseInt(input);
                    config.setHandshakeTimeoutSeconds(seconds);
                    return new MutationResult(true, "Handshake timeout set to " + Math.max(1, seconds) + "s", true, false, false);
                } catch (NumberFormatException ex) {
                    return new MutationResult(false, "Handshake timeout must be a number of seconds", false, false, false);
                }
            }
            case "required_modpack_hash" -> {
                if (input.equalsIgnoreCase("current")) {
                    if (currentPlayerMods == null || currentPlayerMods.isEmpty()) {
                        return new MutationResult(false, "No client mod list available. Join with HandShaker client first.", false, false, false);
                    }

                    String computed = CommandHelper.computeModpackHash(currentPlayerMods, config.isHashMods());
                    config.setRequiredModpackHash(computed);
                    return new MutationResult(true, "required_modpack_hash set to current client hash: " + computed, true, false, true);
                }

                String normalized = CommandHelper.normalizeRequiredModpackHash(input);
                if (normalized == null && !input.equalsIgnoreCase("off") && !input.equalsIgnoreCase("none") && !input.equalsIgnoreCase("null")) {
                    return new MutationResult(false, "required_modpack_hash must be 64-char SHA-256, 'off', or 'current'", false, false, false);
                }

                config.setRequiredModpackHash(normalized);
                return new MutationResult(true, "required_modpack_hash " + (normalized == null ? "disabled" : "set"), true, false, true);
            }
            default -> {
                return new MutationResult(false, "Unknown config parameter: " + param, false, false, false);
            }
        }
    }

    public static MutationResult applyModeToggle(
        CommonConfigManagerBase config,
        Path configDir,
        ConfigFileBootstrap.Logger logger,
        String listName,
        String action
    ) {
        if (config == null) {
            return new MutationResult(false, "Config manager not available", false, false, false);
        }

        Boolean enable = CommandHelper.parseEnableFlag(action);
        if (enable == null) {
            return new MutationResult(false, "Action must be on/off/true/false", false, false, false);
        }

        String normalizedList = listName == null ? "" : listName.toLowerCase(Locale.ROOT);
        switch (normalizedList) {
            case "mods_required" -> {
                config.setModsRequiredEnabledState(enable);
                return new MutationResult(true, "Required Mods turned " + (enable ? "ON" : "OFF"), true, false, true);
            }
            case "mods_blacklisted" -> {
                config.setModsBlacklistedEnabledState(enable);
                return new MutationResult(true, "Blacklisted Mods turned " + (enable ? "ON" : "OFF"), true, false, true);
            }
            case "mods_whitelisted" -> {
                config.setModsWhitelistedEnabledState(enable);
                return new MutationResult(true, "Whitelisted Mods turned " + (enable ? "ON" : "OFF"), true, false, true);
            }
            default -> {
                ModListToggler.ToggleResult toggleResult =
                    ModListToggler.toggleListDetailed(configDir, normalizedList, enable, logger);

                if (toggleResult.status() == ModListToggler.ToggleStatus.NOT_FOUND) {
                    return new MutationResult(false, "Unknown list file: " + listName + " (expected <name>.yml in config/HandShaker)", false, false, false);
                }

                if (toggleResult.status() == ModListToggler.ToggleStatus.UPDATE_FAILED) {
                    String fileName = toggleResult.listFile() != null
                        ? toggleResult.listFile().getFileName().toString()
                        : normalizedList + ".yml";
                    return new MutationResult(false, "Failed to update list file: " + fileName, false, false, false);
                }

                String fileName = toggleResult.listFile() != null
                    ? toggleResult.listFile().getFileName().toString()
                    : normalizedList + ".yml";
                return new MutationResult(true, fileName + " enabled=" + (enable ? "true" : "false"), false, true, true);
            }
        }
    }
}
