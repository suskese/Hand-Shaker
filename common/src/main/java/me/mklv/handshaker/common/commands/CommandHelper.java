package me.mklv.handshaker.common.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;
import me.mklv.handshaker.common.utils.ModpackHashing;

public class CommandHelper {
    
    public static final int PAGE_SIZE = 10;
    
    public record HelpSection(String title, List<HelpEntry> entries) {}
    
    public record HelpEntry(String command, String description) {}
    
    public record InfoField(String label, String value) {}
    
    public record InfoSection(String title, List<InfoField> fields) {}

    public record Page<T>(List<T> items, int pageNum, int totalPages, int startIdx, int endIdx) {}

    public record PagedList<T>(List<T> items, Page<T> page) {}

    public record ModInfoRow(String playerName, String statusLabel, boolean active, String firstSeenFormatted) {}

    public static List<HelpSection> getHelpSections() {
        return List.of(
            new HelpSection("Core Commands", List.of(
                new HelpEntry("/handshaker reload", "Reload config"),
                new HelpEntry("/handshaker info <configured_mods|all_mods|mod|player|diagnostic|export>", "Show info"),
                new HelpEntry("/handshaker config <param> <value>", "Modify config"),
                new HelpEntry("/handshaker mode <list> <on|off>", "Toggle mod lists")
            )),
            new HelpSection("Mod Management", List.of(
                new HelpEntry("/handshaker manage add <mod|*> <mode> [action]", "Add mod"),
                new HelpEntry("/handshaker manage change <mod> <mode> [action]", "Change mod"),
                new HelpEntry("/handshaker manage remove <mod>", "Remove mod"),
                new HelpEntry("/handshaker manage ignore <add|remove|list> [mod|*]", "Manage ignores"),
                new HelpEntry("/handshaker manage player <player>", "View player mods")
            ))
        );
    }

    public static List<String> getConfigFields() {
        return List.of(
            "Force HandShaker Mod",
            "Compatibility",
            "Enforce Whitelisted Mod List",
            "Bedrock Players",
            "Player Database",
            "Required Modpack Hashes"
        );
    }

    public static Boolean parseEnableFlag(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> Boolean.TRUE;
            case "off", "false", "no", "0" -> Boolean.FALSE;
            default -> null;
        };
    }

    public static String normalizeRequiredModpackHash(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.equals("off") || normalized.equals("none") || normalized.equals("null")) {
            return null;
        }

        if (!normalized.matches("[0-9a-f]{64}")) {
            return null;
        }

        return normalized;
    }

    public static String computeModpackHash(Set<String> mods, boolean includeFileHashes) {
        return ModpackHashing.compute(mods, includeFileHashes);
    }

    public static boolean isValidMode(String mode) {
        return CommandSuggestionOperations.MOD_MODES.contains(mode);
    }

    public static String defaultActionForMode(String mode) {
        return CommandModUtil.defaultActionForMode(mode);
    }

    public static List<Map.Entry<String, ConfigState.ModConfig>> sortedConfigMods(Map<String, ConfigState.ModConfig> mods) {
        List<Map.Entry<String, ConfigState.ModConfig>> modList = new ArrayList<>(mods.entrySet());
        modList.sort(Map.Entry.comparingByKey());
        return modList;
    }

    public static List<Map.Entry<String, Integer>> sortedPopularity(Map<String, Integer> popularity) {
        List<Map.Entry<String, Integer>> sortedMods = new ArrayList<>(popularity.entrySet());
        sortedMods.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return sortedMods;
    }

    public static <T> Page<T> paginate(List<T> items, int pageNum, int pageSize) {
        int totalPages = totalPages(items.size(), pageSize);
        if (pageNum < 1 || pageNum > totalPages) {
            return null;
        }
        int startIdx = (pageNum - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, items.size());
        return new Page<>(items, pageNum, totalPages, startIdx, endIdx);
    }

    public static int totalPages(int itemCount, int pageSize) {
        return Math.max(1, (int) Math.ceil((double) itemCount / pageSize));
    }

    public static PagedList<Map.Entry<String, ConfigState.ModConfig>> configuredModsPage(
            Map<String, ConfigState.ModConfig> mods, int pageNum, int pageSize) {
        List<Map.Entry<String, ConfigState.ModConfig>> items = sortedConfigMods(mods);
        return new PagedList<>(items, paginate(items, pageNum, pageSize));
    }

    public static PagedList<Map.Entry<String, Integer>> popularityPage(
            Map<String, Integer> popularity, int pageNum, int pageSize) {
        List<Map.Entry<String, Integer>> items = sortedPopularity(popularity);
        return new PagedList<>(items, paginate(items, pageNum, pageSize));
    }

    public static PagedList<PlayerHistoryDatabase.ModHistoryEntry> historyPage(
            List<PlayerHistoryDatabase.ModHistoryEntry> history, int pageNum, int pageSize) {
        return new PagedList<>(history, paginate(history, pageNum, pageSize));
    }

    public static List<ModInfoRow> buildModInfoRows(
            List<PlayerHistoryDatabase.PlayerModInfo> players,
            String activeLabel,
            String removedLabel) {
        List<ModInfoRow> rows = new ArrayList<>(players.size());
        for (PlayerHistoryDatabase.PlayerModInfo player : players) {
            String statusLabel = player.isActive() ? activeLabel : removedLabel;
            rows.add(new ModInfoRow(
                player.currentName(),
                statusLabel,
                player.isActive(),
                player.getFirstSeenFormatted()
            ));
        }
        return rows;
    }

    public static String modInfoStatusLabel(boolean active) {
        return active ? "✓ Active" : "✗ Removed";
    }

    public static String historyStatusLabel(boolean active) {
        return active ? "ACTIVE" : "REMOVED";
    }

    public static String historyDates(PlayerHistoryDatabase.ModHistoryEntry entry) {
        String dates = "Added: " + entry.getAddedDateFormatted();
        if (!entry.isActive() && entry.getRemovedDateFormatted() != null) {
            dates += " | Removed: " + entry.getRemovedDateFormatted();
        }
        return dates;
    }

    public static String modeTagLabel(String mode) {
        if (mode == null) {
            return "[DET]";
        }

        return switch (mode) {
            case "required" -> "[REQ]";
            case "blacklisted" -> "[BLK]";
            case "allowed" -> "[ALW]";
            default -> "[UNK]";
        };
    }

    public static String prettyModName(String modId) {
        if (modId == null || modId.isBlank()) {
            return "unknown";
        }
        String normalized = modId.replace('_', ' ').replace('-', ' ');
        String[] words = normalized.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    public static String toDisplayModToken(String rawMod) {
        ModEntry entry = ModEntry.parse(rawMod);
        if (entry != null) {
            return entry.toDisplayKey();
        }
        return rawMod != null ? rawMod : "";
    }

    public static Set<String> sanitizeModSuggestions(Collection<String> rawMods) {
        Set<String> result = new LinkedHashSet<>();
        if (rawMods == null) {
            return result;
        }

        for (String rawMod : rawMods) {
            ModEntry entry = ModEntry.parse(rawMod);
            String displayKey = entry != null ? entry.toDisplayKey() : rawMod;
            if (displayKey != null && !displayKey.isBlank()) {
                result.add(displayKey);
            }
        }

        return result;
    }
}
