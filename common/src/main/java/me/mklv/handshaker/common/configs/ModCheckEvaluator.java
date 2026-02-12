package me.mklv.handshaker.common.configs;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModCheckEvaluator {
    private ModCheckEvaluator() {
    }

    public static ModCheckResult evaluate(ModCheckInput input, Set<String> clientMods) {
        if (input == null || clientMods == null) {
            return ModCheckResult.empty();
        }

        Set<String> normalizedMods = new LinkedHashSet<>();
        for (String mod : clientMods) {
            if (mod != null) {
                normalizedMods.add(mod.toLowerCase(Locale.ROOT));
            }
        }

        Set<String> missingRequired = new LinkedHashSet<>();
        if (input.areModsRequiredEnabled()) {
            for (String modId : input.getRequiredModsActive()) {
                if (!normalizedMods.contains(modId)) {
                    missingRequired.add(modId);
                }
            }
        }

        Set<String> blacklistedFound = new LinkedHashSet<>();
        if (input.areModsBlacklistedEnabled()) {
            for (String modId : input.getBlacklistedModsActive()) {
                if (normalizedMods.contains(modId)) {
                    blacklistedFound.add(modId);
                }
            }
        }

        if (!blacklistedFound.isEmpty()) {
            String actionName = resolveActionName(input.getModConfigMap(), blacklistedFound.iterator().next(), "kick");
            String message = replaceModList(input.getKickMessage(), blacklistedFound);
            return ModCheckResult.violation(message, actionName, blacklistedFound, false, true, false);
        }

        if (!missingRequired.isEmpty()) {
            String actionName = resolveActionName(input.getModConfigMap(), missingRequired.iterator().next(), "kick");
            String message = replaceModList(input.getMissingWhitelistModMessage(), missingRequired);
            return ModCheckResult.violation(message, actionName, missingRequired, true, false, false);
        }

        if (input.isWhitelist()) {
            Set<String> nonWhitelisted = new LinkedHashSet<>();
            for (String modId : normalizedMods) {
                if (input.getIgnoredMods().contains(modId)) {
                    continue;
                }
                if (!input.getWhitelistedModsActive().contains(modId)) {
                    nonWhitelisted.add(modId);
                }
            }

            if (!nonWhitelisted.isEmpty()) {
                String actionName = resolveActionName(input.getModConfigMap(), nonWhitelisted.iterator().next(), "kick");
                String message = replaceModList(input.getKickMessage(), nonWhitelisted);
                return ModCheckResult.violation(message, actionName, nonWhitelisted, false, false, true);
            }
        }

        if (input.areModsWhitelistedEnabled()) {
            Map<String, String> allowedActions = new LinkedHashMap<>();
            for (String modId : normalizedMods) {
                ConfigState.ModConfig cfg = input.getModConfigMap().get(modId);
                if (cfg != null && cfg.isAllowed()) {
                    String action = cfg.getActionName();
                    if (action != null && !action.equalsIgnoreCase("none")) {
                        allowedActions.put(modId, action);
                    }
                }
            }

            if (!allowedActions.isEmpty()) {
                return ModCheckResult.allowedActions(allowedActions);
            }
        }

        return ModCheckResult.empty();
    }

    private static String resolveActionName(Map<String, ConfigState.ModConfig> modConfigMap,
                                            String modId,
                                            String defaultAction) {
        if (modId == null || modConfigMap == null) {
            return defaultAction;
        }

        ConfigState.ModConfig cfg = modConfigMap.get(modId.toLowerCase(Locale.ROOT));
        if (cfg == null) {
            return defaultAction;
        }

        String actionName = cfg.getActionName();
        return actionName != null && !actionName.isEmpty() ? actionName : defaultAction;
    }

    private static String replaceModList(String message, Set<String> mods) {
        if (message == null) {
            return null;
        }
        String modList = String.join(", ", mods);
        return message.replace("{mod}", modList);
    }
}
