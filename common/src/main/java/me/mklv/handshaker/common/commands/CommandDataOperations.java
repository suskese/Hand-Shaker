package me.mklv.handshaker.common.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class CommandDataOperations {
    private CommandDataOperations() {
    }

    public static <P> Set<String> collectDistinctMods(Iterable<P> players, Function<P, ? extends Collection<String>> modsProvider) {
        Set<String> mods = new LinkedHashSet<>();
        if (players == null || modsProvider == null) {
            return mods;
        }

        for (P player : players) {
            Collection<String> playerMods = modsProvider.apply(player);
            if (playerMods == null || playerMods.isEmpty()) {
                continue;
            }
            mods.addAll(playerMods);
        }

        return mods;
    }

    public static <P> List<Set<String>> collectModSets(Iterable<P> players, Function<P, Set<String>> modsProvider) {
        List<Set<String>> result = new ArrayList<>();
        if (players == null || modsProvider == null) {
            return result;
        }

        for (P player : players) {
            result.add(modsProvider.apply(player));
        }

        return result;
    }

    public static <P> List<String> collectPlayerNames(Iterable<P> players, Function<P, String> nameProvider) {
        List<String> names = new ArrayList<>();
        if (players == null || nameProvider == null) {
            return names;
        }

        for (P player : players) {
            String name = nameProvider.apply(player);
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }

        return names;
    }
}