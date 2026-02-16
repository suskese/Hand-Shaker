package me.mklv.handshaker.common.configs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ModChecks {
    private ModChecks() {
    }

    // region ModCheckInput
    public static final class ModCheckInput {
        private final boolean whitelist;
        private final boolean modsRequiredEnabled;
        private final boolean modsBlacklistedEnabled;
        private final boolean modsWhitelistedEnabled;
        private final boolean hashMods;
        private final boolean modVersioning;
        private final Map<String, String> knownModHashes;
        private final Set<String> ignoredMods;
        private final Set<String> whitelistedModsActive;
        private final Set<String> optionalModsActive;
        private final Set<String> blacklistedModsActive;
        private final Set<String> requiredModsActive;
        private final Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap;
        private final String kickMessage;
        private final String missingWhitelistModMessage;

        public ModCheckInput(boolean whitelist,
                             boolean modsRequiredEnabled,
                             boolean modsBlacklistedEnabled,
                             boolean modsWhitelistedEnabled,
                             boolean hashMods,
                             boolean modVersioning,
                             Map<String, String> knownModHashes,
                             Set<String> ignoredMods,
                             Set<String> whitelistedModsActive,
                             Set<String> optionalModsActive,
                             Set<String> blacklistedModsActive,
                             Set<String> requiredModsActive,
                             Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                             String kickMessage,
                             String missingWhitelistModMessage) {
            this.whitelist = whitelist;
            this.modsRequiredEnabled = modsRequiredEnabled;
            this.modsBlacklistedEnabled = modsBlacklistedEnabled;
            this.modsWhitelistedEnabled = modsWhitelistedEnabled;
            this.hashMods = hashMods;
            this.modVersioning = modVersioning;
            this.knownModHashes = knownModHashes;
            this.ignoredMods = ignoredMods;
            this.whitelistedModsActive = whitelistedModsActive;
            this.optionalModsActive = optionalModsActive;
            this.blacklistedModsActive = blacklistedModsActive;
            this.requiredModsActive = requiredModsActive;
            this.modConfigMap = modConfigMap;
            this.kickMessage = kickMessage;
            this.missingWhitelistModMessage = missingWhitelistModMessage;
        }

        public boolean isWhitelist() {
            return whitelist;
        }

        public boolean areModsRequiredEnabled() {
            return modsRequiredEnabled;
        }

        public boolean areModsBlacklistedEnabled() {
            return modsBlacklistedEnabled;
        }

        public boolean areModsWhitelistedEnabled() {
            return modsWhitelistedEnabled;
        }

        public boolean isHashMods() {
            return hashMods;
        }

        public boolean isModVersioning() {
            return modVersioning;
        }

        public Map<String, String> getKnownModHashes() {
            return knownModHashes;
        }

        public Set<String> getIgnoredMods() {
            return ignoredMods;
        }

        public Set<String> getWhitelistedModsActive() {
            return whitelistedModsActive;
        }

        public Set<String> getOptionalModsActive() {
            return optionalModsActive;
        }

        public Set<String> getBlacklistedModsActive() {
            return blacklistedModsActive;
        }

        public Set<String> getRequiredModsActive() {
            return requiredModsActive;
        }

        public Map<String, ConfigTypes.ConfigState.ModConfig> getModConfigMap() {
            return modConfigMap;
        }

        public String getKickMessage() {
            return kickMessage;
        }

        public String getMissingWhitelistModMessage() {
            return missingWhitelistModMessage;
        }
    }
    // endregion

    // region ModCheckResult
    public static final class ModCheckResult {
        private final String message;
        private final String actionName;
        private final Set<String> mods;
        private final boolean requiredViolation;
        private final boolean blacklistedViolation;
        private final boolean whitelistViolation;
        private final Map<String, String> allowedActionsByMod;

        private ModCheckResult(String message,
                               String actionName,
                               Set<String> mods,
                               boolean requiredViolation,
                               boolean blacklistedViolation,
                               boolean whitelistViolation,
                               Map<String, String> allowedActionsByMod) {
            this.message = message;
            this.actionName = actionName;
            this.mods = mods != null ? Collections.unmodifiableSet(mods) : Collections.emptySet();
            this.requiredViolation = requiredViolation;
            this.blacklistedViolation = blacklistedViolation;
            this.whitelistViolation = whitelistViolation;
            this.allowedActionsByMod = allowedActionsByMod != null
                ? Collections.unmodifiableMap(allowedActionsByMod)
                : Collections.emptyMap();
        }

        public static ModCheckResult violation(String message,
                                               String actionName,
                                               Set<String> mods,
                                               boolean requiredViolation,
                                               boolean blacklistedViolation,
                                               boolean whitelistViolation) {
            return new ModCheckResult(
                message,
                actionName,
                mods != null ? new LinkedHashSet<>(mods) : new LinkedHashSet<>(),
                requiredViolation,
                blacklistedViolation,
                whitelistViolation,
                new LinkedHashMap<>()
            );
        }

        public static ModCheckResult allowedActions(Map<String, String> allowedActionsByMod) {
            String firstAction = null;
            Set<String> mods = new LinkedHashSet<>();
            if (allowedActionsByMod != null && !allowedActionsByMod.isEmpty()) {
                for (Map.Entry<String, String> entry : allowedActionsByMod.entrySet()) {
                    mods.add(entry.getKey());
                    if (firstAction == null) {
                        firstAction = entry.getValue();
                    }
                }
            }
            return new ModCheckResult(null, firstAction, mods, false, false, false,
                allowedActionsByMod != null ? new LinkedHashMap<>(allowedActionsByMod) : new LinkedHashMap<>());
        }

        public static ModCheckResult empty() {
            return new ModCheckResult(null, null, new LinkedHashSet<>(), false, false, false, new LinkedHashMap<>());
        }

        public String getMessage() {
            return message;
        }

        public String getActionName() {
            return actionName;
        }

        public Set<String> getMods() {
            return mods;
        }

        public boolean isRequiredViolation() {
            return requiredViolation;
        }

        public boolean isBlacklistedViolation() {
            return blacklistedViolation;
        }

        public boolean isWhitelistViolation() {
            return whitelistViolation;
        }

        public Map<String, String> getAllowedActionsByMod() {
            return allowedActionsByMod;
        }

        public boolean isViolation() {
            return message != null && !message.isEmpty();
        }

        public boolean hasAllowedActions() {
            return !allowedActionsByMod.isEmpty();
        }
    }
    // endregion

    // region ModCheckEvaluator
    public static final class ModCheckEvaluator {
        private ModCheckEvaluator() {
        }

        public static ModCheckResult evaluate(ModCheckInput input, Set<String> clientMods) {
            if (input == null || clientMods == null) {
                return ModCheckResult.empty();
            }

            List<ConfigTypes.ModEntry> parsedMods = new ArrayList<>();
            for (String mod : clientMods) {
                ConfigTypes.ModEntry parsed = ConfigTypes.ModEntry.parse(mod);
                if (parsed != null) {
                    parsedMods.add(parsed);
                }
            }

            Set<String> missingRequired = new LinkedHashSet<>();
            if (input.areModsRequiredEnabled()) {
                for (String ruleKey : input.getRequiredModsActive()) {
                    if (!matchesRule(ruleKey, input, parsedMods)) {
                        missingRequired.add(ruleKey);
                    }
                }
            }

            Set<String> blacklistedFound = new LinkedHashSet<>();
            if (input.areModsBlacklistedEnabled()) {
                for (String ruleKey : input.getBlacklistedModsActive()) {
                    if (matchesRule(ruleKey, input, parsedMods)) {
                        blacklistedFound.add(ruleKey);
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
                for (ConfigTypes.ModEntry clientMod : parsedMods) {
                    if (input.getIgnoredMods().contains(clientMod.modId())) {
                        continue;
                    }
                    if (!isAllowedInWhitelist(clientMod, input)) {
                        nonWhitelisted.add(clientMod.toDisplayKey());
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
                for (ConfigTypes.ModEntry clientMod : parsedMods) {
                    ConfigTypes.ConfigState.ModConfig cfg = findMatchingConfig(clientMod, input);
                    if (cfg != null && (cfg.isAllowed() || cfg.isRequired()) && isHashAccepted(clientMod, input)) {
                        String action = cfg.getActionName();
                        if (action != null && !action.equalsIgnoreCase("none")) {
                            allowedActions.put(clientMod.toDisplayKey(), action);
                        }
                    }
                }

                if (!allowedActions.isEmpty()) {
                    return ModCheckResult.allowedActions(allowedActions);
                }
            }

            return ModCheckResult.empty();
        }

        private static boolean matchesRule(String ruleKey, ModCheckInput input, List<ConfigTypes.ModEntry> clientMods) {
            ConfigTypes.ModEntry rule = ConfigTypes.ModEntry.parse(ruleKey);
            if (rule == null) {
                return false;
            }

            for (ConfigTypes.ModEntry clientMod : clientMods) {
                if (!rule.modId().equals(clientMod.modId())) {
                    continue;
                }
                if (input.isModVersioning() && rule.version() != null && !rule.version().equals(clientMod.version())) {
                    continue;
                }
                if (!isHashAccepted(clientMod, rule, input)) {
                    continue;
                }
                return true;
            }
            return false;
        }

        private static boolean isAllowedInWhitelist(ConfigTypes.ModEntry clientMod, ModCheckInput input) {
            for (String ruleKey : input.getWhitelistedModsActive()) {
                ConfigTypes.ModEntry rule = ConfigTypes.ModEntry.parse(ruleKey);
                if (rule == null) {
                    continue;
                }
                if (!rule.modId().equals(clientMod.modId())) {
                    continue;
                }
                if (input.isModVersioning() && rule.version() != null && !rule.version().equals(clientMod.version())) {
                    continue;
                }
                if (!isHashAccepted(clientMod, rule, input)) {
                    continue;
                }
                return true;
            }

            if (input.getOptionalModsActive() != null) {
                for (String ruleKey : input.getOptionalModsActive()) {
                    ConfigTypes.ModEntry rule = ConfigTypes.ModEntry.parse(ruleKey);
                    if (rule == null) {
                        continue;
                    }
                    if (!rule.modId().equals(clientMod.modId())) {
                        continue;
                    }
                    if (input.isModVersioning() && rule.version() != null && !rule.version().equals(clientMod.version())) {
                        continue;
                    }
                    if (!isHashAccepted(clientMod, rule, input)) {
                        continue;
                    }
                    return true;
                }
            }

            return false;
        }

        private static ConfigTypes.ConfigState.ModConfig findMatchingConfig(ConfigTypes.ModEntry clientMod, ModCheckInput input) {
            ConfigTypes.ConfigState.ModConfig exact = input.getModConfigMap().get(clientMod.toDisplayKey());
            if (exact != null) {
                return exact;
            }
            return input.getModConfigMap().get(clientMod.modId());
        }

        private static boolean isHashAccepted(ConfigTypes.ModEntry clientMod, ModCheckInput input) {
            return isHashAccepted(clientMod, clientMod, input);
        }

        private static boolean isHashAccepted(ConfigTypes.ModEntry clientMod, ConfigTypes.ModEntry rule, ModCheckInput input) {
            if (!input.isHashMods()) {
                return true;
            }

            if (clientMod.hash() == null) {
                return true;
            }

            Map<String, String> knownHashes = input.getKnownModHashes();
            if (knownHashes == null || knownHashes.isEmpty()) {
                return true;
            }

            String expected = knownHashes.get(rule.toRuleKey(input.isModVersioning()));
            if (expected == null || expected.isBlank()) {
                expected = knownHashes.get(rule.modId());
            }
            if (expected == null || expected.isBlank()) {
                return true;
            }
            return expected.equalsIgnoreCase(clientMod.hash());
        }

        private static String resolveActionName(Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                                String modId,
                                                String defaultAction) {
            if (modId == null || modConfigMap == null) {
                return defaultAction;
            }

            ConfigTypes.ConfigState.ModConfig cfg = modConfigMap.get(modId.toLowerCase(Locale.ROOT));
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
    // endregion
}
