package me.mklv.handshaker.common.commands;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ModManagementOperations {
    private ModManagementOperations() {
    }

    public static int applyToMods(Collection<String> mods, Predicate<String> shouldSkip, Consumer<String> apply) {
        if (mods == null || mods.isEmpty()) {
            return 0;
        }

        int applied = 0;
        for (String mod : mods) {
            if (mod == null || mod.isBlank()) {
                continue;
            }
            if (shouldSkip != null && shouldSkip.test(mod)) {
                continue;
            }
            apply.accept(mod);
            applied++;
        }

        return applied;
    }
}
