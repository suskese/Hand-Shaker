package me.mklv.handshaker.common.utils;

import me.mklv.handshaker.common.configs.ConfigTypes;

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
        private final boolean hybridCompatibility;
        private final Set<String> requiredModpackHashes;
        private final Map<String, String> knownModHashes;
        private final Set<String> ignoredMods;
        private final Set<String> whitelistedModsActive;
        private final Set<String> optionalModsActive;
        private final Set<String> blacklistedModsActive;
        private final Set<String> requiredModsActive;
        private final Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap;
        private final String kickMessage;
        private final String kickSpoofedModMessage;
        private final String missingWhitelistModMessage;
        private final String modpackHashMismatchMessage;

        public ModCheckInput(boolean whitelist,
                             boolean modsRequiredEnabled,
                             boolean modsBlacklistedEnabled,
                             boolean modsWhitelistedEnabled,
                             boolean hashMods,
                             boolean modVersioning,
                             boolean hybridCompatibility,
                             Set<String> requiredModpackHashes,
                             Map<String, String> knownModHashes,
                             Set<String> ignoredMods,
                             Set<String> whitelistedModsActive,
                             Set<String> optionalModsActive,
                             Set<String> blacklistedModsActive,
                             Set<String> requiredModsActive,
                             Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                             String kickMessage,
                             String kickSpoofedModMessage,
                             String missingWhitelistModMessage,
                             String modpackHashMismatchMessage) {
            this.whitelist = whitelist;
            this.modsRequiredEnabled = modsRequiredEnabled;
            this.modsBlacklistedEnabled = modsBlacklistedEnabled;
            this.modsWhitelistedEnabled = modsWhitelistedEnabled;
            this.hashMods = hashMods;
            this.modVersioning = modVersioning;
            this.hybridCompatibility = hybridCompatibility;
            this.requiredModpackHashes = requiredModpackHashes != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(requiredModpackHashes))
                : Collections.emptySet();
            this.knownModHashes = knownModHashes;
            this.ignoredMods = ignoredMods;
            this.whitelistedModsActive = whitelistedModsActive;
            this.optionalModsActive = optionalModsActive;
            this.blacklistedModsActive = blacklistedModsActive;
            this.requiredModsActive = requiredModsActive;
            this.modConfigMap = modConfigMap;
            this.kickMessage = kickMessage;
            this.kickSpoofedModMessage = kickSpoofedModMessage;
            this.missingWhitelistModMessage = missingWhitelistModMessage;
            this.modpackHashMismatchMessage = modpackHashMismatchMessage;
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

        public boolean isHybridCompatibility() {
            return hybridCompatibility;
        }

        public Set<String> getRequiredModpackHashes() {
            return requiredModpackHashes;
        }

        public String getRequiredModpackHash() {
            return requiredModpackHashes.isEmpty() ? null : requiredModpackHashes.iterator().next();
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

        public String getKickSpoofedModMessage() {
            return kickSpoofedModMessage;
        }

        public String getMissingWhitelistModMessage() {
            return missingWhitelistModMessage;
        }

        public String getModpackHashMismatchMessage() {
            return modpackHashMismatchMessage;
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
            String firstBlacklistedRule = null;
            if (input.areModsBlacklistedEnabled()) {
                for (String ruleKey : input.getBlacklistedModsActive()) {
                    if (matchesRule(ruleKey, input, parsedMods)) {
                        String matchedMod = findFirstMatchingClientMod(ruleKey, input, parsedMods);
                        blacklistedFound.add(matchedMod != null ? matchedMod : ruleKey);
                        if (firstBlacklistedRule == null) {
                            firstBlacklistedRule = ruleKey;
                        }
                    }
                }
            }

            if (!blacklistedFound.isEmpty()) {
                String actionName = resolveActionName(
                    input.getModConfigMap(),
                    firstBlacklistedRule != null ? firstBlacklistedRule : blacklistedFound.iterator().next(),
                    "kick"
                );
                String message = replaceModList(input.getKickMessage(), blacklistedFound);
                return ModCheckResult.violation(message, actionName, blacklistedFound, false, true, false);
            }

            if (!missingRequired.isEmpty()) {
                String actionName = resolveActionName(input.getModConfigMap(), missingRequired.iterator().next(), "kick");
                String message = replaceModList(input.getMissingWhitelistModMessage(), missingRequired);
                return ModCheckResult.violation(message, actionName, missingRequired, true, false, false);
            }

            Set<String> hashMismatches = collectHashMismatches(input, parsedMods);
            if (!hashMismatches.isEmpty()) {
                String baseMessage = input.getKickSpoofedModMessage() != null
                    ? input.getKickSpoofedModMessage()
                    : input.getKickMessage();
                String message = replaceModList(baseMessage, hashMismatches);
                return ModCheckResult.violation(message, "kick", hashMismatches, false, true, false);
            }

            if (input.isWhitelist()) {
                Set<String> nonWhitelisted = new LinkedHashSet<>();
                for (ConfigTypes.ModEntry clientMod : parsedMods) {
                    if (isIgnored(clientMod, input.getIgnoredMods())) {
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

            if (!input.getRequiredModpackHashes().isEmpty()) {
                String computedHash = computeModpackHash(clientMods, input.isHashMods());
                if (!input.getRequiredModpackHashes().contains(computedHash.toLowerCase(Locale.ROOT))) {
                    Set<String> mismatch = new LinkedHashSet<>();
                    mismatch.add("modpack");
                    String baseMessage = input.getModpackHashMismatchMessage() != null
                        ? input.getModpackHashMismatchMessage()
                        : input.getKickMessage();
                    String message = replaceModList(baseMessage, mismatch);
                    return ModCheckResult.violation(message, "kick", mismatch, false, true, false);
                }
            }

            return ModCheckResult.empty();
        }

        private static boolean matchesRule(String ruleKey, ModCheckInput input, List<ConfigTypes.ModEntry> clientMods) {
            for (ConfigTypes.ModEntry clientMod : clientMods) {
                if (ruleMatchesClient(ruleKey, clientMod, input)) {
                    return true;
                }
            }
            return false;
        }

        private static String findFirstMatchingClientMod(String ruleKey,
                                                         ModCheckInput input,
                                                         List<ConfigTypes.ModEntry> clientMods) {
            if (clientMods == null || clientMods.isEmpty()) {
                return null;
            }
            for (ConfigTypes.ModEntry clientMod : clientMods) {
                if (ruleMatchesClient(ruleKey, clientMod, input)) {
                    return clientMod.toDisplayKey();
                }
            }
            return null;
        }

        private static boolean isAllowedInWhitelist(ConfigTypes.ModEntry clientMod, ModCheckInput input) {
            for (String ruleKey : input.getWhitelistedModsActive()) {
                if (ruleMatchesClient(ruleKey, clientMod, input)) {
                    return true;
                }
            }

            if (input.getOptionalModsActive() != null) {
                for (String ruleKey : input.getOptionalModsActive()) {
                    if (ruleMatchesClient(ruleKey, clientMod, input)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private static ConfigTypes.ConfigState.ModConfig findMatchingConfig(ConfigTypes.ModEntry clientMod, ModCheckInput input) {
            ConfigTypes.ConfigState.ModConfig exact = input.getModConfigMap().get(clientMod.toDisplayKey());
            if (exact != null) {
                return exact;
            }

            ConfigTypes.ConfigState.ModConfig byModId = input.getModConfigMap().get(clientMod.modId());
            if (byModId != null) {
                return byModId;
            }

            for (Map.Entry<String, ConfigTypes.ConfigState.ModConfig> entry : input.getModConfigMap().entrySet()) {
                if (!WildcardMatcher.containsWildcard(entry.getKey())) {
                    continue;
                }
                if (ruleMatchesClient(entry.getKey(), clientMod, input)) {
                    return entry.getValue();
                }
            }

            return null;
        }

        private static boolean ruleMatchesClient(String ruleKey, ConfigTypes.ModEntry clientMod, ModCheckInput input) {
            ConfigTypes.ModEntry rule = ConfigTypes.ModEntry.parse(ruleKey);
            if (rule == null || clientMod == null) {
                return false;
            }

            if (!modIdMatches(rule.modId(), clientMod.modId())) {
                return false;
            }
            if (input.isModVersioning() && rule.version() != null
                && clientMod.version() != null
                && !rule.version().equals(clientMod.version())) {
                return false;
            }
            if (input.isModVersioning() && rule.version() != null
                && clientMod.version() == null
                && !input.isHybridCompatibility()) {
                return false;
            }
            return isHashAccepted(clientMod, rule, input);
        }

        private static boolean modIdMatches(String ruleModId, String clientModId) {
            if (ruleModId == null || clientModId == null) {
                return false;
            }
            if (ruleModId.equals(clientModId)) {
                return true;
            }
            return WildcardMatcher.containsWildcard(ruleModId) && WildcardMatcher.matches(ruleModId, clientModId);
        }

        private static boolean isHashAccepted(ConfigTypes.ModEntry clientMod, ModCheckInput input) {
            return isHashAccepted(clientMod, clientMod, input);
        }

        private static boolean isHashAccepted(ConfigTypes.ModEntry clientMod, ConfigTypes.ModEntry rule, ModCheckInput input) {
            if (!input.isHashMods()) {
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
            if (clientMod.hash() == null || clientMod.hash().isBlank()) {
                return false;
            }
            return expected.equalsIgnoreCase(clientMod.hash());
        }

        private static Set<String> collectHashMismatches(ModCheckInput input, List<ConfigTypes.ModEntry> parsedMods) {
            Set<String> mismatches = new LinkedHashSet<>();
            if (!input.isHashMods() || parsedMods == null || parsedMods.isEmpty()) {
                return mismatches;
            }

            Map<String, String> knownHashes = input.getKnownModHashes();
            if (knownHashes == null || knownHashes.isEmpty()) {
                return mismatches;
            }

            for (ConfigTypes.ModEntry clientMod : parsedMods) {
                if (clientMod == null || isIgnored(clientMod, input.getIgnoredMods())) {
                    continue;
                }

                String key = clientMod.toRuleKey(input.isModVersioning());
                String expected = knownHashes.get(key);
                if ((expected == null || expected.isBlank()) && input.isModVersioning()) {
                    expected = knownHashes.get(clientMod.modId());
                }
                if (expected == null || expected.isBlank()) {
                    continue;
                }

                String actual = clientMod.hash();
                if (actual == null || actual.isBlank() || !expected.equalsIgnoreCase(actual)) {
                    mismatches.add(clientMod.toDisplayKey());
                }
            }

            return mismatches;
        }

        private static boolean isIgnored(ConfigTypes.ModEntry clientMod, Set<String> ignoredMods) {
            if (clientMod == null || ignoredMods == null || ignoredMods.isEmpty()) {
                return false;
            }

            if (ignoredMods.contains(clientMod.modId()) || ignoredMods.contains(clientMod.toDisplayKey())) {
                return true;
            }

            for (String ignored : ignoredMods) {
                if (!WildcardMatcher.containsWildcard(ignored)) {
                    continue;
                }
                if (WildcardMatcher.matches(ignored, clientMod.modId()) || WildcardMatcher.matches(ignored, clientMod.toDisplayKey())) {
                    return true;
                }
            }
            return false;
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
            final int MOD_DISPLAY_LIMIT = 5;
            final int MOD_TEXT_LIMIT = 220;
            StringBuilder modList = new StringBuilder();
            int count = 0;
            for (String mod : mods) {
                if (count >= MOD_DISPLAY_LIMIT) {
                    int remaining = mods.size() - MOD_DISPLAY_LIMIT;
                    modList.append(", and ").append(remaining).append(" more");
                    break;
                }
                if (count > 0) {
                    modList.append(", ");
                }
                modList.append(mod);
                count++;

                if (modList.length() >= MOD_TEXT_LIMIT) {
                    int remaining = Math.max(0, mods.size() - count);
                    if (remaining > 0) {
                        modList.append(", and ").append(remaining).append(" more");
                    }
                    break;
                }
            }
            return message.replace("{mod}", modList.toString());
        }

        private static String computeModpackHash(Set<String> clientMods, boolean includeFileHashes) {
            return ModpackHashing.compute(clientMods, includeFileHashes);
        }
    }
    // endregion
}
