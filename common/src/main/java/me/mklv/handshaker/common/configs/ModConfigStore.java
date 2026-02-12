package me.mklv.handshaker.common.configs;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModConfigStore {
    private ModConfigStore() {
    }

    public static String normalizeModId(String modId) {
        if (modId == null) {
            return null;
        }
        return modId.toLowerCase(Locale.ROOT);
    }

    public static void upsertModConfig(Map<String, ConfigState.ModConfig> modConfigMap,
                                      Set<String> requiredModsActive,
                                      Set<String> blacklistedModsActive,
                                      Set<String> whitelistedModsActive,
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

        ConfigState.ModConfig existing = modConfigMap.get(normalizedId);
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
            modConfigMap.put(normalizedId, new ConfigState.ModConfig(mode, resolvedAction, warnMessage));
        }

        if (mode != null) {
            updateActiveSets(normalizedId, mode, requiredModsActive, blacklistedModsActive, whitelistedModsActive);
        }
    }

    public static boolean removeModConfig(Map<String, ConfigState.ModConfig> modConfigMap,
                                          Set<String> requiredModsActive,
                                          Set<String> blacklistedModsActive,
                                          Set<String> whitelistedModsActive,
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
        }
        return removed;
    }

    public static void addAllMods(Set<String> mods,
                                  String mode,
                                  String action,
                                  String warnMessage,
                                  Map<String, ConfigState.ModConfig> modConfigMap,
                                  Set<String> requiredModsActive,
                                  Set<String> blacklistedModsActive,
                                  Set<String> whitelistedModsActive,
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
            modConfigMap.put(normalizedId, new ConfigState.ModConfig(mode, resolvedAction, warnMessage));
            updateActiveSets(normalizedId, mode, requiredModsActive, blacklistedModsActive, whitelistedModsActive);
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

        String modeLower = mode != null ? mode.toLowerCase(Locale.ROOT) : ConfigState.MODE_ALLOWED;
        if (ConfigState.MODE_ALLOWED.equals(modeLower) || ConfigState.MODE_WHITELISTED.equals(modeLower)) {
            return defaultAllowedAction;
        }
        return defaultOtherAction;
    }

    private static void updateActiveSets(String modId,
                                         String mode,
                                         Set<String> requiredModsActive,
                                         Set<String> blacklistedModsActive,
                                         Set<String> whitelistedModsActive) {
        requiredModsActive.remove(modId);
        blacklistedModsActive.remove(modId);
        whitelistedModsActive.remove(modId);

        String modeLower = mode.toLowerCase(Locale.ROOT);
        if (ConfigState.MODE_REQUIRED.equals(modeLower)) {
            requiredModsActive.add(modId);
        } else if (ConfigState.MODE_BLACKLISTED.equals(modeLower)) {
            blacklistedModsActive.add(modId);
        } else if (ConfigState.MODE_ALLOWED.equals(modeLower) || ConfigState.MODE_WHITELISTED.equals(modeLower)) {
            whitelistedModsActive.add(modId);
        }
    }
}
