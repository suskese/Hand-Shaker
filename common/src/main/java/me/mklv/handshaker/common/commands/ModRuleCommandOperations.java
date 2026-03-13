package me.mklv.handshaker.common.commands;

import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;

import java.util.Collection;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ModRuleCommandOperations {
    private ModRuleCommandOperations() {
    }

    @FunctionalInterface
    public interface ModRuleSetter {
        void set(String modId, String mode, String action, String warnMessage);
    }

    public static void upsertModRule(
        String modId,
        String mode,
        String action,
        Function<String, String> defaultActionForMode,
        ModRuleSetter setter,
        Consumer<String> onSet
    ) {
        String normalizedMode = mode == null ? "" : mode.toLowerCase(Locale.ROOT);
        String resolvedAction = action != null
            ? action.toLowerCase(Locale.ROOT)
            : defaultActionForMode.apply(normalizedMode);

        setter.set(modId, normalizedMode, resolvedAction, null);
        if (onSet != null) {
            onSet.accept(modId);
        }
    }

    public static void changeModRulePreserveAction(
        String modId,
        String mode,
        ConfigState.ModConfig existing,
        Function<String, String> defaultActionForMode,
        ModRuleSetter setter
    ) {
        String normalizedMode = mode == null ? "" : mode.toLowerCase(Locale.ROOT);
        String action = existing != null && existing.getAction() != null
            ? existing.getAction().toString().toLowerCase(Locale.ROOT)
            : defaultActionForMode.apply(normalizedMode);
        String warnMessage = existing != null ? existing.getWarnMessage() : null;
        setter.set(modId, normalizedMode, action, warnMessage);
    }

    public static int upsertBulkModRules(
        Collection<String> mods,
        Predicate<String> shouldSkip,
        String mode,
        String action,
        Function<String, String> defaultActionForMode,
        ModRuleSetter setter,
        Consumer<String> onSet
    ) {
        return ModManagementOperations.applyToMods(mods, shouldSkip, mod ->
            upsertModRule(mod, mode, action, defaultActionForMode, setter, onSet)
        );
    }

    public static boolean removeModRule(String modId, Predicate<String> removeOperation) {
        return removeOperation.test(modId);
    }
}