package me.mklv.handshaker.common.configs;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModConfigStore {
    private ModConfigStore() {
    }

    public static String normalizeModId(String modId) {
        ConfigTypes.ModEntry entry = ConfigTypes.ModEntry.parse(modId);
        if (entry == null) {
            return null;
        }
        return entry.toDisplayKey();
    }

    public static void upsertModConfig(Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                       Set<String> requiredModsActive,
                                       Set<String> blacklistedModsActive,
                                       Set<String> whitelistedModsActive,
                                       Set<String> optionalModsActive,
                                       String modId,
                                       String mode,
                                       String action,
                                       String warnMessage,
                                       String defaultAllowedAction,
                                       String defaultOtherAction) {
        String normalizedId = normalizeModId(modId);
        if (normalizedId == null) {
            return;
        }

        ConfigTypes.ConfigState.ModConfig existing = modConfigMap.get(normalizedId);
        if (existing != null) {
            if (mode != null) {
                existing.setMode(mode);
            }
            if (action != null) {
                existing.setAction(action);
            }
            if (warnMessage != null) {
                existing.setWarnMessage(warnMessage);
            }
        } else {
            String resolvedAction = resolveActionDefault(mode, action, defaultAllowedAction, defaultOtherAction);
            modConfigMap.put(normalizedId, new ConfigTypes.ConfigState.ModConfig(mode, resolvedAction, warnMessage));
        }

        if (mode != null) {
            updateActiveSets(normalizedId, mode, requiredModsActive, blacklistedModsActive, whitelistedModsActive, optionalModsActive);
        }
    }

    public static boolean removeModConfig(Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                          Set<String> requiredModsActive,
                                          Set<String> blacklistedModsActive,
                                          Set<String> whitelistedModsActive,
                                          Set<String> optionalModsActive,
                                          String modId) {
        String normalizedId = normalizeModId(modId);
        if (normalizedId == null) {
            return false;
        }

        boolean removed = modConfigMap.remove(normalizedId) != null;
        if (removed) {
            requiredModsActive.remove(normalizedId);
            blacklistedModsActive.remove(normalizedId);
            whitelistedModsActive.remove(normalizedId);
            optionalModsActive.remove(normalizedId);
        }
        return removed;
    }

    public static void addAllMods(Set<String> mods,
                                  String mode,
                                  String action,
                                  String warnMessage,
                                  Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                  Set<String> requiredModsActive,
                                  Set<String> blacklistedModsActive,
                                  Set<String> whitelistedModsActive,
                                  Set<String> optionalModsActive,
                                  String defaultAllowedAction,
                                  String defaultOtherAction) {
        if (mods == null) {
            return;
        }

        String resolvedAction = resolveActionDefault(mode, action, defaultAllowedAction, defaultOtherAction);
        for (String mod : mods) {
            String normalizedId = normalizeModId(mod);
            if (normalizedId == null) {
                continue;
            }
            modConfigMap.put(normalizedId, new ConfigTypes.ConfigState.ModConfig(mode, resolvedAction, warnMessage));
            updateActiveSets(normalizedId, mode, requiredModsActive, blacklistedModsActive, whitelistedModsActive, optionalModsActive);
        }
    }

    public static String resolveActionDefault(String mode,
                                              String action,
                                              String defaultAllowedAction,
                                              String defaultOtherAction) {
        if (action != null) {
            return action;
        }
        if (defaultAllowedAction == null && defaultOtherAction == null) {
            return null;
        }

        String modeLower = mode != null ? mode.toLowerCase(Locale.ROOT) : ConfigTypes.ConfigState.MODE_ALLOWED;
        if (ConfigTypes.ConfigState.MODE_ALLOWED.equals(modeLower)
            || ConfigTypes.ConfigState.MODE_WHITELISTED.equals(modeLower)
            || ConfigTypes.ConfigState.MODE_REQUIRED.equals(modeLower)) {
            return defaultAllowedAction;
        }
        return defaultOtherAction;
    }

    private static void updateActiveSets(String modId,
                                         String mode,
                                         Set<String> requiredModsActive,
                                         Set<String> blacklistedModsActive,
                                         Set<String> whitelistedModsActive,
                                         Set<String> optionalModsActive) {
        requiredModsActive.remove(modId);
        blacklistedModsActive.remove(modId);
        whitelistedModsActive.remove(modId);
        optionalModsActive.remove(modId);

        String modeLower = mode.toLowerCase(Locale.ROOT);
        if (ConfigTypes.ConfigState.MODE_REQUIRED.equals(modeLower)) {
            requiredModsActive.add(modId);
        } else if (ConfigTypes.ConfigState.MODE_BLACKLISTED.equals(modeLower)) {
            blacklistedModsActive.add(modId);
        } else if (ConfigTypes.ConfigState.MODE_ALLOWED.equals(modeLower) || ConfigTypes.ConfigState.MODE_WHITELISTED.equals(modeLower)) {
            whitelistedModsActive.add(modId);
        } else if ("optional".equals(modeLower)) {
            optionalModsActive.add(modId);
            whitelistedModsActive.add(modId);
        }
    }
}
