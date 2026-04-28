package me.mklv.handshaker.common.commands;

import java.util.Collection;
import java.util.function.Function;
import java.util.function.Predicate;

public final class IgnoreCommandOperations {
    private IgnoreCommandOperations() {
    }

    public static boolean addIgnoredMod(String mod, Predicate<String> isIgnored, Function<String, Boolean> addIgnored) {
        if (mod == null || mod.isBlank()) {
            return false;
        }
        if (isIgnored != null && isIgnored.test(mod)) {
            return false;
        }
        Boolean added = addIgnored.apply(mod);
        return Boolean.TRUE.equals(added);
    }

    public static boolean removeIgnoredMod(String mod, Function<String, Boolean> removeIgnored) {
        if (mod == null || mod.isBlank()) {
            return false;
        }
        Boolean removed = removeIgnored.apply(mod);
        return Boolean.TRUE.equals(removed);
    }

    public static int addIgnoredMods(
        Collection<String> mods,
        Predicate<String> isIgnored,
        Function<String, Boolean> addIgnored
    ) {
        return ModManagementOperations.applyToMods(mods, isIgnored, mod -> addIgnored.apply(mod));
    }
}