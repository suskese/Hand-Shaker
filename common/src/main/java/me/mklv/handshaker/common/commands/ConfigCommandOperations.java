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
            case "force_handshaker_mod", "behavior" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    if (input.equalsIgnoreCase("STRICT") || input.equalsIgnoreCase("VANILLA")) {
                        enabled = input.equalsIgnoreCase("STRICT");
                    } else {
                        return new MutationResult(false, "force_handshaker_mod must be true/false or on/off", false, false, false);
                    }
                }
                config.setForceHandshakerMod(enabled);
                return new MutationResult(true, "force_handshaker_mod set to " + enabled, true, false, true);
            }
            case "compat_modern" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "compat_modern must be true/false or on/off", false, false, false);
                }
                config.setModernCompatibilityEnabled(enabled);
                return new MutationResult(true, "compat_modern set to " + enabled, true, false, false);
            }
            case "compat_hybrid" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "compat_hybrid must be true/false or on/off", false, false, false);
                }
                config.setHybridCompatibilityEnabled(enabled);
                return new MutationResult(true, "compat_hybrid set to " + enabled, true, false, false);
            }
            case "compat_legacy" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "compat_legacy must be true/false or on/off", false, false, false);
                }
                config.setLegacyCompatibilityEnabled(enabled);
                return new MutationResult(true, "compat_legacy set to " + enabled, true, false, false);
            }
            case "compat_unsigned", "integrity" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    if (input.equalsIgnoreCase("SIGNED") || input.equalsIgnoreCase("DEV")) {
                        enabled = input.equalsIgnoreCase("DEV");
                    } else {
                        return new MutationResult(false, "compat_unsigned must be true/false or on/off", false, false, false);
                    }
                }
                config.setUnsignedCompatibilityEnabled(enabled);
                return new MutationResult(true, "compat_unsigned set to " + enabled, true, false, false);
            }
            case "default_action", "default" -> {
                String normalizedAction = input.toLowerCase(Locale.ROOT);
                boolean builtIn = normalizedAction.equals("none") || normalizedAction.equals("kick") || normalizedAction.equals("ban");
                boolean custom = config.getAvailableActions().stream()
                    .anyMatch(action -> action.equalsIgnoreCase(normalizedAction));
                if (!builtIn && !custom) {
                    String available = config.getAvailableActions().isEmpty()
                        ? "none, kick, ban"
                        : "none, kick, ban, " + String.join(", ", config.getAvailableActions());
                    return new MutationResult(false, "Default action must be one of: " + available, false, false, false);
                }
                config.setDefaultAction(normalizedAction);
                return new MutationResult(true, "default_action set to " + normalizedAction, true, false, false);
            }
            case "enforce_whitelisted_mod_list", "whitelist" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "enforce_whitelisted_mod_list must be true/false or on/off", false, false, false);
                }
                config.setWhitelist(enabled);
                return new MutationResult(true, "enforce_whitelisted_mod_list " + (enabled ? "enabled" : "disabled"), true, false, true);
            }
            case "allow_bedrock_players", "allow_bedrock" -> {
                Boolean allow = CommandHelper.parseEnableFlag(input);
                if (allow == null) {
                    return new MutationResult(false, "allow_bedrock_players must be true/false or on/off", false, false, false);
                }
                config.setAllowBedrockPlayers(allow);
                return new MutationResult(true, "Bedrock players " + (allow ? "allowed" : "blocked"), true, false, true);
            }
            case "player_database_enabled", "playerdb_enabled" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "player_database_enabled must be true/false or on/off", false, false, false);
                }
                config.setPlayerdbEnabled(enabled);
                return new MutationResult(true, "Player database " + (enabled ? "enabled" : "disabled"), true, false, false);
            }
            case "use_hash_for_mods", "hash_mods" -> {
                Boolean enabled = CommandHelper.parseEnableFlag(input);
                if (enabled == null) {
                    return new MutationResult(false, "use_hash_for_mods must be true/false or on/off", false, false, false);
                }
                config.setHashMods(enabled);
                return new MutationResult(true, "use_hash_for_mods " + (enabled ? "enabled" : "disabled"), true, false, false);
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
            case "handshake_timeout_seconds", "handshake_timeout" -> {
                try {
                    int seconds = Integer.parseInt(input);
                    config.setHandshakeTimeoutSeconds(seconds);
                    return new MutationResult(true, "handshake_timeout_seconds set to " + Math.max(1, seconds), true, false, false);
                } catch (NumberFormatException ex) {
                    return new MutationResult(false, "Handshake timeout must be a number of seconds", false, false, false);
                }
            }
            case "required_modpack_hash", "required_modpack_hashes" -> {
                if (input.equalsIgnoreCase("current")) {
                    if (currentPlayerMods == null || currentPlayerMods.isEmpty()) {
                        return new MutationResult(false, "No client mod list available. Join with HandShaker client first.", false, false, false);
                    }

                    String computed = CommandHelper.computeModpackHash(currentPlayerMods, config.isHashMods());
                    config.setRequiredModpackHash(computed);
                    return new MutationResult(true, "required_modpack_hashes set to current client hash: " + computed, true, false, true);
                }

                String normalized = CommandHelper.normalizeRequiredModpackHash(input);
                if (normalized == null && !input.equalsIgnoreCase("off") && !input.equalsIgnoreCase("none") && !input.equalsIgnoreCase("null")) {
                    return new MutationResult(false, "required_modpack_hashes must be a 64-char SHA-256, 'off', or 'current'", false, false, false);
                }

                config.setRequiredModpackHash(normalized);
                return new MutationResult(true, "required_modpack_hashes " + (normalized == null ? "disabled" : "set"), true, false, true);
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
