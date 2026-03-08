package me.mklv.handshaker.common.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandSuggestionOperations {
    public static final List<String> ROOT_COMMANDS = List.of("reload", "info", "config", "mode", "manage");
    public static final List<String> INFO_SUBCOMMANDS = List.of("configured_mods", "all_mods", "mod", "player");
    public static final List<String> CONFIG_PARAMS = List.of(
        "force_handshaker_mod",
        "compat_modern",
        "compat_hybrid",
        "compat_legacy",
        "compat_unsigned",
        "default_action",
        "enforce_whitelisted_mod_list",
        "allow_bedrock_players",
        "player_database_enabled",
        "handshake_timeout_seconds",
        "use_hash_for_mods",
        "mod_versioning",
        "runtime_cache",
        "required_modpack_hash"
    );
    public static final List<String> MODE_LISTS = List.of("mods_required", "mods_blacklisted", "mods_whitelisted");
    public static final List<String> MANAGE_SUBCOMMANDS = List.of("add", "change", "remove", "ignore", "player");
    public static final List<String> MOD_MODES = List.of("allowed", "required", "blacklisted", "optional");
    public static final List<String> BOOLEAN_VALUES = List.of("true", "false");
    public static final List<String> INTEGRITY_MODES = List.of("signed", "dev");
    public static final List<String> BEHAVIOR_MODES = List.of("strict", "vanilla");
    public static final List<String> IGNORE_SUBCOMMANDS = List.of("add", "remove", "list");
    public static final List<String> DEFAULT_ACTIONS = List.of("kick", "ban", "none");

    private CommandSuggestionOperations() {
    }

    public static List<String> filterByPrefix(Collection<String> candidates, String remaining) {
        List<String> matches = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) {
            return matches;
        }

        String normalized = remaining == null ? "" : remaining.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                matches.add(candidate);
            }
        }

        return matches;
    }

    public static List<String> modeSuggestions(String remaining) {
        return filterByPrefix(MOD_MODES, remaining);
    }

    public static List<String> modeListSuggestions(String remaining) {
        return filterByPrefix(MODE_LISTS, remaining);
    }

    public static List<String> booleanSuggestions(String remaining) {
        return filterByPrefix(BOOLEAN_VALUES, remaining);
    }

    public static List<String> configParamSuggestions(String remaining) {
        return filterByPrefix(CONFIG_PARAMS, remaining);
    }

    public static List<String> actionSuggestions(Collection<String> availableActions, boolean includeDefaults, boolean fallbackToDefaultsWhenEmpty) {
        Set<String> combined = new LinkedHashSet<>();
        boolean hasCustom = availableActions != null && !availableActions.isEmpty();

        if (includeDefaults || (!hasCustom && fallbackToDefaultsWhenEmpty)) {
            combined.addAll(DEFAULT_ACTIONS);
        }

        if (hasCustom) {
            combined.addAll(availableActions);
        }

        return new ArrayList<>(combined);
    }

    public static List<String> configValueSuggestions(String param, Collection<String> availableActions) {
        if (param == null) {
            return List.of();
        }

        String normalized = param.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "force_handshaker_mod", "compat_modern", "compat_hybrid", "compat_legacy", "compat_unsigned",
                 "enforce_whitelisted_mod_list", "allow_bedrock_players", "player_database_enabled", "use_hash_for_mods",
                 "mod_versioning", "runtime_cache",
                 "whitelist", "allow_bedrock", "playerdb_enabled", "hash_mods" ->
                BOOLEAN_VALUES;
            case "required_modpack_hash" -> List.of("off", "current");
            case "default_action", "default" -> actionSuggestions(availableActions, true, true);
            default -> List.of();
        };
    }

    public static String autoQuotedSuggestion(String remaining, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String remainingValue = remaining == null ? "" : remaining;
        boolean needsQuotes = value.contains(":") || value.contains("+") || value.contains(" ") || value.contains("!");

        if (remainingValue.startsWith("\"") || needsQuotes) {
            String checkAgainst = remainingValue.startsWith("\"") ? remainingValue.substring(1) : remainingValue;
            if (value.toLowerCase(Locale.ROOT).startsWith(checkAgainst.toLowerCase(Locale.ROOT))) {
                return "\"" + value + "\"";
            }
            return null;
        }

        if (value.toLowerCase(Locale.ROOT).startsWith(remainingValue.toLowerCase(Locale.ROOT))) {
            return value;
        }
        return null;
    }
}
