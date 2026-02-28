package me.mklv.handshaker.common.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CommandSuggestionOperations {
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
        return filterByPrefix(CommandSuggestionData.MOD_MODES, remaining);
    }

    public static List<String> modeListSuggestions(String remaining) {
        return filterByPrefix(CommandSuggestionData.MODE_LISTS, remaining);
    }

    public static List<String> booleanSuggestions(String remaining) {
        return filterByPrefix(CommandSuggestionData.BOOLEAN_VALUES, remaining);
    }

    public static List<String> configParamSuggestions(String remaining) {
        return filterByPrefix(CommandSuggestionData.CONFIG_PARAMS, remaining);
    }

    public static List<String> actionSuggestions(Collection<String> availableActions, boolean includeDefaults, boolean fallbackToDefaultsWhenEmpty) {
        Set<String> combined = new LinkedHashSet<>();
        boolean hasCustom = availableActions != null && !availableActions.isEmpty();

        if (includeDefaults || (!hasCustom && fallbackToDefaultsWhenEmpty)) {
            combined.addAll(CommandSuggestionData.DEFAULT_ACTIONS);
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
            case "behavior" -> CommandSuggestionData.BEHAVIOR_MODES;
            case "integrity" -> CommandSuggestionData.INTEGRITY_MODES;
            case "whitelist", "allow_bedrock", "playerdb_enabled", "hash_mods", "mod_versioning", "runtime_cache" ->
                CommandSuggestionData.BOOLEAN_VALUES;
            case "required_modpack_hash" -> List.of("off", "current");
            case "default" -> actionSuggestions(availableActions, true, true);
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
