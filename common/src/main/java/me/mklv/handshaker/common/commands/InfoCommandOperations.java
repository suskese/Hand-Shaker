package me.mklv.handshaker.common.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;
import me.mklv.handshaker.common.configs.ConfigTypes.ModEntry;
import me.mklv.handshaker.common.database.PlayerHistoryDatabase;

public final class InfoCommandOperations {
    private InfoCommandOperations() {
    }

    public record ModInfoResult(boolean success, String error, String displayKey, List<PlayerHistoryDatabase.PlayerModInfo> players) {
    }

    public record PlayerHistoryResult(
        boolean success,
        String error,
        String notFoundPlayerName,
        List<PlayerHistoryDatabase.ModHistoryEntry> history,
        CommandHelper.Page<PlayerHistoryDatabase.ModHistoryEntry> page
    ) {
    }

    public record ConfiguredModsPageResult(
        List<Map.Entry<String, ConfigState.ModConfig>> items,
        CommandHelper.Page<Map.Entry<String, ConfigState.ModConfig>> page,
        int totalPages
    ) {
        public boolean isEmpty() {
            return items == null || items.isEmpty();
        }

        public boolean hasInvalidPage() {
            return !isEmpty() && page == null;
        }
    }

    public record PopularityPageResult(
        List<Map.Entry<String, Integer>> items,
        CommandHelper.Page<Map.Entry<String, Integer>> page,
        int totalPages
    ) {
        public boolean isEmpty() {
            return items == null || items.isEmpty();
        }

        public boolean hasInvalidPage() {
            return !isEmpty() && page == null;
        }
    }

    public static ModInfoResult loadModInfo(
        PlayerHistoryDatabase db,
        String modInput,
        boolean aggregateVersionsWhenVersionMissing
    ) {
        return loadModInfo(db, modInput, aggregateVersionsWhenVersionMissing, null);
    }

    public static ModInfoResult loadModInfo(
        PlayerHistoryDatabase db,
        String modInput,
        boolean aggregateVersionsWhenVersionMissing,
        Predicate<String> ignorePredicate
    ) {
        if (db == null || !db.isEnabled()) {
            return new ModInfoResult(false, "Player history database not available", modInput, List.of());
        }

        ModEntry entry = ModEntry.parse(modInput);
        String displayKey = entry != null ? entry.toDisplayKey() : modInput;

        List<PlayerHistoryDatabase.PlayerModInfo> players;
        if (aggregateVersionsWhenVersionMissing && entry != null && entry.version() == null) {
            String searchModId = entry.modId();
            Set<String> matchedTokens = new LinkedHashSet<>();
            for (String token : db.getModPopularity().keySet()) {
                if (ignorePredicate != null && ignorePredicate.test(token)) {
                    continue;
                }
                ModEntry candidate = ModEntry.parse(token);
                if (candidate != null && candidate.modId().equalsIgnoreCase(searchModId)) {
                    matchedTokens.add(token);
                }
            }

            Map<String, PlayerHistoryDatabase.PlayerModInfo> unique = new LinkedHashMap<>();
            for (String token : matchedTokens) {
                List<PlayerHistoryDatabase.PlayerModInfo> tokenPlayers = db.getPlayersWithMod(token);
                for (PlayerHistoryDatabase.PlayerModInfo playerInfo : tokenPlayers) {
                    unique.put(playerInfo.currentName() + ":" + token, playerInfo);
                }
            }
            players = new ArrayList<>(unique.values());
        } else {
            if (ignorePredicate != null && ignorePredicate.test(displayKey)) {
                return new ModInfoResult(true, null, displayKey, List.of());
            }
            players = db.getPlayersWithMod(displayKey);
        }

        return new ModInfoResult(true, null, displayKey, players);
    }

    public static PlayerHistoryResult loadPlayerHistory(
        PlayerHistoryDatabase db,
        String playerName,
        UUID onlinePlayerUuid,
        int pageNum,
        int pageSize
    ) {
        return loadPlayerHistory(db, playerName, onlinePlayerUuid, pageNum, pageSize, null);
    }

    public static PlayerHistoryResult loadPlayerHistory(
        PlayerHistoryDatabase db,
        String playerName,
        UUID onlinePlayerUuid,
        int pageNum,
        int pageSize,
        Predicate<String> ignorePredicate
    ) {
        if (db == null || !db.isEnabled()) {
            return new PlayerHistoryResult(false, "Player history database not available", playerName, List.of(), null);
        }

        UUID uuid = onlinePlayerUuid != null ? onlinePlayerUuid : db.getPlayerUuidByName(playerName).orElse(null);
        if (uuid == null) {
            return new PlayerHistoryResult(false, "No player history found for: " + playerName, playerName, List.of(), null);
        }

        List<PlayerHistoryDatabase.ModHistoryEntry> history = db.getPlayerHistory(uuid);
        if (ignorePredicate != null && !history.isEmpty()) {
            List<PlayerHistoryDatabase.ModHistoryEntry> filtered = new ArrayList<>();
            for (PlayerHistoryDatabase.ModHistoryEntry entry : history) {
                String modToken = entry.modName();
                if (modToken != null && ignorePredicate.test(modToken)) {
                    continue;
                }
                filtered.add(entry);
            }
            history = filtered;
        }

        if (history.isEmpty()) {
            return new PlayerHistoryResult(true, null, playerName, history, null);
        }

        CommandHelper.PagedList<PlayerHistoryDatabase.ModHistoryEntry> paged =
            CommandHelper.historyPage(history, pageNum, pageSize);
        CommandHelper.Page<PlayerHistoryDatabase.ModHistoryEntry> page = paged.page();
        if (page == null) {
            int totalPages = CommandHelper.totalPages(history.size(), pageSize);
            return new PlayerHistoryResult(false, "Invalid page. Total pages: " + totalPages, playerName, history, null);
        }

        return new PlayerHistoryResult(true, null, playerName, history, page);
    }

    public static ConfiguredModsPageResult loadConfiguredModsPage(
        Map<String, ConfigState.ModConfig> mods,
        int pageNum,
        int pageSize
    ) {
        return loadConfiguredModsPage(mods, pageNum, pageSize, null);
    }

    public static ConfiguredModsPageResult loadConfiguredModsPage(
        Map<String, ConfigState.ModConfig> mods,
        int pageNum,
        int pageSize,
        Predicate<String> ignorePredicate
    ) {
        Map<String, ConfigState.ModConfig> filtered = new LinkedHashMap<>();
        if (mods != null) {
            for (Map.Entry<String, ConfigState.ModConfig> entry : mods.entrySet()) {
                String modToken = entry.getKey();
                if (ignorePredicate != null && ignorePredicate.test(modToken)) {
                    continue;
                }
                filtered.put(modToken, entry.getValue());
            }
        }

        CommandHelper.PagedList<Map.Entry<String, ConfigState.ModConfig>> paged =
            CommandHelper.configuredModsPage(filtered, pageNum, pageSize);
        List<Map.Entry<String, ConfigState.ModConfig>> items = paged.items();
        int totalPages = CommandHelper.totalPages(items.size(), pageSize);
        return new ConfiguredModsPageResult(items, paged.page(), totalPages);
    }

    public static PopularityPageResult loadPopularityPage(
        Map<String, Integer> popularity,
        int pageNum,
        int pageSize
    ) {
        return loadPopularityPage(popularity, pageNum, pageSize, null);
    }

    public static PopularityPageResult loadPopularityPage(
        Map<String, Integer> popularity,
        int pageNum,
        int pageSize,
        Predicate<String> ignorePredicate
    ) {
        Map<String, Integer> filtered = new LinkedHashMap<>();
        if (popularity != null) {
            for (Map.Entry<String, Integer> entry : popularity.entrySet()) {
                String modToken = entry.getKey();
                if (ignorePredicate != null && ignorePredicate.test(modToken)) {
                    continue;
                }
                filtered.put(modToken, entry.getValue());
            }
        }

        CommandHelper.PagedList<Map.Entry<String, Integer>> paged =
            CommandHelper.popularityPage(filtered, pageNum, pageSize);
        List<Map.Entry<String, Integer>> items = paged.items();
        int totalPages = CommandHelper.totalPages(items.size(), pageSize);
        return new PopularityPageResult(items, paged.page(), totalPages);
    }

    public static Map<String, Integer> collectPopularity(Iterable<? extends Collection<String>> modCollections) {
        Map<String, Integer> popularity = new LinkedHashMap<>();
        if (modCollections == null) {
            return popularity;
        }

        for (Collection<String> mods : modCollections) {
            if (mods == null) {
                continue;
            }
            for (String mod : mods) {
                if (mod == null || mod.isBlank()) {
                    continue;
                }
                popularity.put(mod, popularity.getOrDefault(mod, 0) + 1);
            }
        }

        return popularity;
    }
}
