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
        "bedrock_policy",
        "database",
        "default_action",
        "handshaker_enforcement",
        "mod_versioning",
        "modpack_hashes",
        "runtime_cache",
        "timeout_seconds",
        "use_hash_for_mods",
        "whitelist_enforcement"
    );
    public static final List<String> COMPAT_SUBPARAMS = List.of("modern", "hybrid", "legacy", "unsigned");
    public static final List<String> MODE_LISTS = List.of("mods_required", "mods_blacklisted", "mods_whitelisted");
    public static final List<String> MANAGE_SUBCOMMANDS = List.of("add", "change", "remove", "ignore", "player");
    public static final List<String> MOD_MODES = List.of("allowed", "required", "blacklisted", "optional");
    public static final List<String> BOOLEAN_VALUES = List.of("true", "false");
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

    public static List<String> compatSubparamSuggestions(String remaining) {
        return filterByPrefix(COMPAT_SUBPARAMS, remaining);
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
            case "handshaker_enforcement", "compat_modern", "compat_hybrid", "compat_legacy", "compat_unsigned",
                 "whitelist_enforcement", "bedrock_policy", "database", "use_hash_for_mods",
                 "mod_versioning", "runtime_cache" ->
                BOOLEAN_VALUES;
            case "modpack_hashes" -> List.of("off", "current");
            case "default_action", "default" -> actionSuggestions(availableActions, true, true);
            default -> List.of();
        };
    }

    public static String autoQuotedSuggestion(String remaining, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String remainingValue = remaining == null ? "" : remaining;
        boolean needsQuotes = value.contains(":")
            || value.contains("+")
            || value.contains(" ")
            || value.contains("!")
            || value.contains("*")
            || value.contains("?");

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

    public static List<String> autoQuotedSuggestions(String remaining, Collection<String> values) {
        List<String> suggestions = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return suggestions;
        }

        for (String value : values) {
            String suggestion = autoQuotedSuggestion(remaining, value);
            if (suggestion != null) {
                suggestions.add(suggestion);
            }
        }
        return suggestions;
    }

    public static List<String> modeStateSuggestions(Boolean currentlyEnabled) {
        if (currentlyEnabled == null) {
            return List.of("on", "off");
        }
        return currentlyEnabled ? List.of("off") : List.of("on");
    }
}
