package me.mklv.handshaker.common.configs;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigRuntime {
    private ConfigRuntime() {
    }

    // region ConfigSnapshotBuilder
    public static final class ConfigSnapshotBuilder {
        private ConfigSnapshotBuilder() {
        }

        public static ConfigTypes.ConfigLoadResult build(ConfigTypes.ConfigState.Behavior behavior,
                                                         ConfigTypes.ConfigState.IntegrityMode integrityMode,
                                                         boolean debug,
                                                         String kickMessage,
                                                         String noHandshakeKickMessage,
                                                         String missingWhitelistModMessage,
                                                         String invalidSignatureKickMessage,
                                                         boolean allowBedrockPlayers,
                                                         boolean playerdbEnabled,
                                                         boolean modsRequiredEnabled,
                                                         boolean modsBlacklistedEnabled,
                                                         boolean modsWhitelistedEnabled,
                                                         boolean hashMods,
                                                         boolean modVersioning,
                                                         boolean whitelist,
                                                         int handshakeTimeoutSeconds,
                                                         Map<String, String> messages,
                                                         Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                                         Set<String> ignoredMods,
                                                         Set<String> whitelistedModsActive,
                                                         Set<String> blacklistedModsActive,
                                                         Set<String> requiredModsActive,
                                                         Set<String> optionalModsActive,
                                                         Map<String, ConfigTypes.ActionDefinition> actionsMap) {
            ConfigTypes.ConfigLoadResult snapshot = new ConfigTypes.ConfigLoadResult();
            snapshot.setDebug(debug);
            snapshot.setBehavior(behavior);
            snapshot.setIntegrityMode(integrityMode);
            snapshot.setKickMessage(kickMessage);
            snapshot.setNoHandshakeKickMessage(noHandshakeKickMessage);
            snapshot.setMissingWhitelistModMessage(missingWhitelistModMessage);
            snapshot.setInvalidSignatureKickMessage(invalidSignatureKickMessage);
            snapshot.setAllowBedrockPlayers(allowBedrockPlayers);
            snapshot.setPlayerdbEnabled(playerdbEnabled);
            snapshot.setModsRequiredEnabled(modsRequiredEnabled);
            snapshot.setModsBlacklistedEnabled(modsBlacklistedEnabled);
            snapshot.setModsWhitelistedEnabled(modsWhitelistedEnabled);
            snapshot.setHashMods(hashMods);
            snapshot.setModVersioning(modVersioning);
            snapshot.setWhitelist(whitelist);
            snapshot.setHandshakeTimeoutSeconds(handshakeTimeoutSeconds);

            Map<String, String> messageSnapshot = messages != null ? messages : Collections.emptyMap();
            snapshot.getMessages().putAll(messageSnapshot);
            snapshot.getModConfigMap().putAll(modConfigMap);
            snapshot.getIgnoredMods().addAll(ignoredMods);
            snapshot.getWhitelistedModsActive().addAll(whitelistedModsActive);
            snapshot.getBlacklistedModsActive().addAll(blacklistedModsActive);
            snapshot.getRequiredModsActive().addAll(requiredModsActive);
            snapshot.getOptionalModsActive().addAll(optionalModsActive);
            snapshot.getActionsMap().putAll(actionsMap);
            return snapshot;
        }
    }
    // endregion

    // region MessagePlaceholderExpander
    public static final class MessagePlaceholderExpander {
        private static final Pattern MESSAGE_PATTERN = Pattern.compile("\\{messages\\.([^}]+)\\}");

        private MessagePlaceholderExpander() {
        }

        public static String expand(String command,
                                    String playerName,
                                    String modText,
                                    Map<String, String> messages) {
            if (command == null) {
                return null;
            }

            String result = replaceMessagePlaceholders(command, messages);
            String safePlayer = playerName != null ? playerName : "";
            String safeMod = modText != null ? modText : "";

            return result
                .replace("{player}", safePlayer)
                .replace("{mod}", safeMod)
                .replace("{mods}", safeMod);
        }

        private static String replaceMessagePlaceholders(String command, Map<String, String> messages) {
            if (messages == null || messages.isEmpty()) {
                return command;
            }

            Matcher matcher = MESSAGE_PATTERN.matcher(command);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String messageKey = matcher.group(1);
                String messageValue = messages.getOrDefault(messageKey, "{messages." + messageKey + "}");
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(messageValue));
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        }
    }
    // endregion

    // region ModConfigStore
    public static final class ModConfigStore {
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
                // Optional mods are also added to whitelisted for backwards compatibility
                whitelistedModsActive.add(modId);
            }
        }
    }
    // endregion

    // region CommonConfigManagerBase
    public abstract static class CommonConfigManagerBase {
        protected boolean debug = false;
        protected ConfigTypes.ConfigState.Behavior behavior = ConfigTypes.ConfigState.Behavior.STRICT;
        protected ConfigTypes.ConfigState.IntegrityMode integrityMode = ConfigTypes.ConfigState.IntegrityMode.SIGNED;
        protected String kickMessage = ConfigTypes.StandardMessages.DEFAULT_KICK_MESSAGE;
        protected String noHandshakeKickMessage = ConfigTypes.StandardMessages.DEFAULT_NO_HANDSHAKE_MESSAGE;
        protected String missingWhitelistModMessage = ConfigTypes.StandardMessages.DEFAULT_MISSING_WHITELIST_MESSAGE;
        protected String invalidSignatureKickMessage = ConfigTypes.StandardMessages.DEFAULT_INVALID_SIGNATURE_MESSAGE;
        protected boolean allowBedrockPlayers = false;
        protected boolean playerdbEnabled = false;
        protected int handshakeTimeoutSeconds = 5;

        protected boolean modsRequiredEnabled = true;
        protected boolean modsBlacklistedEnabled = true;
        protected boolean modsWhitelistedEnabled = true;
        protected boolean hashMods = true;
        protected boolean modVersioning = true;

        protected final Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap = new LinkedHashMap<>();
        protected boolean whitelist = false;
        protected final Set<String> ignoredMods = new HashSet<>();
        protected final Set<String> whitelistedModsActive = new HashSet<>();
        protected final Set<String> optionalModsActive = new HashSet<>();
        protected final Set<String> blacklistedModsActive = new HashSet<>();
        protected final Set<String> requiredModsActive = new HashSet<>();
        protected final Map<String, ConfigTypes.ActionDefinition> actionsMap = new LinkedHashMap<>();

        protected final Map<String, String> customMessages;
        protected final Map<String, String> messagesMap;

        protected CommonConfigManagerBase() {
            Map<String, String> backing = new LinkedHashMap<>();
            this.customMessages = backing;
            this.messagesMap = backing;
        }

        protected final void loadCommon(Path configDir,
                                        Class<?> resourceBase,
                                        ConfigFileBootstrap.Logger logger,
                                        ConfigTypes.ConfigLoadOptions options) {
            ConfigTypes.ConfigLoadResult result = ConfigLoader.load(configDir, resourceBase, logger, options);
            applyLoadResult(result);
        }

        protected final void applyLoadResult(ConfigTypes.ConfigLoadResult result) {
            if (result == null) {
                return;
            }

            debug = result.isDebug();

            behavior = result.getBehavior();
            integrityMode = result.getIntegrityMode();
            kickMessage = result.getKickMessage();
            noHandshakeKickMessage = result.getNoHandshakeKickMessage();
            missingWhitelistModMessage = result.getMissingWhitelistModMessage();
            invalidSignatureKickMessage = result.getInvalidSignatureKickMessage();
            allowBedrockPlayers = result.isAllowBedrockPlayers();
            playerdbEnabled = result.isPlayerdbEnabled();
            handshakeTimeoutSeconds = result.getHandshakeTimeoutSeconds();
            modsRequiredEnabled = result.areModsRequiredEnabled();
            modsBlacklistedEnabled = result.areModsBlacklistedEnabled();
            modsWhitelistedEnabled = result.areModsWhitelistedEnabled();
            hashMods = result.isHashMods();
            modVersioning = result.isModVersioning();
            whitelist = result.isWhitelist();

            customMessages.clear();
            customMessages.putAll(result.getMessages());

            modConfigMap.clear();
            modConfigMap.putAll(result.getModConfigMap());

            ignoredMods.clear();
            ignoredMods.addAll(result.getIgnoredMods());

            whitelistedModsActive.clear();
            whitelistedModsActive.addAll(result.getWhitelistedModsActive());

            optionalModsActive.clear();
            optionalModsActive.addAll(result.getOptionalModsActive());

            blacklistedModsActive.clear();
            blacklistedModsActive.addAll(result.getBlacklistedModsActive());

            requiredModsActive.clear();
            requiredModsActive.addAll(result.getRequiredModsActive());

            actionsMap.clear();
            actionsMap.putAll(result.getActionsMap());
        }

        protected final void saveCommon(Path configDir,
                                        ConfigFileBootstrap.Logger logger) {
            ConfigTypes.ConfigLoadResult snapshot = ConfigSnapshotBuilder.build(
                behavior,
                integrityMode,
                debug,
                kickMessage,
                noHandshakeKickMessage,
                missingWhitelistModMessage,
                invalidSignatureKickMessage,
                allowBedrockPlayers,
                playerdbEnabled,
                modsRequiredEnabled,
                modsBlacklistedEnabled,
                modsWhitelistedEnabled,
                hashMods,
                modVersioning,
                whitelist,
                handshakeTimeoutSeconds,
                customMessages,
                modConfigMap,
                ignoredMods,
                whitelistedModsActive,
                blacklistedModsActive,
                requiredModsActive,
                optionalModsActive,
                actionsMap
            );

            ConfigWriter.writeAll(configDir, logger, snapshot);
        }

        public boolean isDebug() {
            return debug;
        }

        public ConfigTypes.ConfigState.Behavior getBehavior() {
            return behavior;
        }

        public ConfigTypes.ConfigState.IntegrityMode getIntegrityMode() {
            return integrityMode;
        }

        public String getKickMessage() {
            return kickMessage;
        }

        public String getNoHandshakeKickMessage() {
            return noHandshakeKickMessage;
        }

        public String getMissingWhitelistModMessage() {
            return missingWhitelistModMessage;
        }

        public String getInvalidSignatureKickMessage() {
            return invalidSignatureKickMessage;
        }

        public Map<String, ConfigTypes.ConfigState.ModConfig> getModConfigMap() {
            return Collections.unmodifiableMap(modConfigMap);
        }

        public boolean isWhitelist() {
            return whitelist;
        }

        public Set<String> getIgnoredMods() {
            return Collections.unmodifiableSet(ignoredMods);
        }

        public boolean isAllowBedrockPlayers() {
            return allowBedrockPlayers;
        }

        public int getHandshakeTimeoutSeconds() {
            return handshakeTimeoutSeconds;
        }

        public Set<String> getWhitelistedMods() {
            return Collections.unmodifiableSet(whitelistedModsActive);
        }

        public Set<String> getOptionalMods() {
            return Collections.unmodifiableSet(optionalModsActive);
        }

        public Set<String> getBlacklistedMods() {
            return Collections.unmodifiableSet(blacklistedModsActive);
        }

        public Set<String> getRequiredMods() {
            return Collections.unmodifiableSet(requiredModsActive);
        }

        public boolean isPlayerdbEnabled() {
            return playerdbEnabled;
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

        public ConfigTypes.ActionDefinition getAction(String actionName) {
            if (actionName == null) return null;
            return actionsMap.get(actionName.toLowerCase(Locale.ROOT));
        }

        public String getMessageOrDefault(String key, String fallback) {
            if (key == null) {
                return fallback;
            }
            String message = customMessages.get(key);
            return message != null ? message : fallback;
        }

        public Set<String> getAvailableActions() {
            return Collections.unmodifiableSet(actionsMap.keySet());
        }

        public void setBehavior(String value) {
            this.behavior = value != null && value.equalsIgnoreCase("STRICT") ? ConfigTypes.ConfigState.Behavior.STRICT : ConfigTypes.ConfigState.Behavior.VANILLA;
        }

        public void setIntegrityMode(String value) {
            this.integrityMode = value != null && value.equalsIgnoreCase("SIGNED") ? ConfigTypes.ConfigState.IntegrityMode.SIGNED : ConfigTypes.ConfigState.IntegrityMode.DEV;
        }

        public void setWhitelist(boolean value) {
            this.whitelist = value;
        }

        public void setDefaultMode(String value) {
            this.whitelist = value != null && value.equalsIgnoreCase("BLACKLISTED");
        }

        public String getDefaultMode() {
            return whitelist ? "BLACKLISTED" : "ALLOWED";
        }

        public void setKickMessage(String message) {
            this.kickMessage = message;
            if (message != null) {
                customMessages.put("kick", message);
            }
        }

        public void setNoHandshakeKickMessage(String message) {
            this.noHandshakeKickMessage = message;
            if (message != null) {
                customMessages.put("no-handshake", message);
            }
        }

        public void setMissingWhitelistModMessage(String message) {
            this.missingWhitelistModMessage = message;
            if (message != null) {
                customMessages.put("missing-whitelist", message);
            }
        }

        public void setInvalidSignatureKickMessage(String message) {
            this.invalidSignatureKickMessage = message;
            if (message != null) {
                customMessages.put("invalid-signature", message);
            }
        }

        public void setAllowBedrockPlayers(boolean allow) {
            this.allowBedrockPlayers = allow;
        }

        public void setHandshakeTimeoutSeconds(int seconds) {
            this.handshakeTimeoutSeconds = Math.max(1, seconds);
        }

        public void setPlayerdbEnabled(boolean enabled) {
            this.playerdbEnabled = enabled;
        }

        public void setModsRequiredEnabledState(boolean enabled) {
            this.modsRequiredEnabled = enabled;
        }

        public void setModsBlacklistedEnabledState(boolean enabled) {
            this.modsBlacklistedEnabled = enabled;
        }

        public void setModsWhitelistedEnabledState(boolean enabled) {
            this.modsWhitelistedEnabled = enabled;
        }

        public void setHashMods(boolean enabled) {
            this.hashMods = enabled;
        }

        public void setModVersioning(boolean enabled) {
            this.modVersioning = enabled;
        }

        public boolean addIgnoredMod(String modId) {
            if (modId == null) {
                return false;
            }
            String normalized = modId.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return false;
            }
            return ignoredMods.add(normalized);
        }

        public boolean removeIgnoredMod(String modId) {
            if (modId == null) {
                return false;
            }
            String normalized = modId.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return false;
            }
            return ignoredMods.remove(normalized);
        }

        public boolean isIgnored(String modId) {
            if (modId == null) {
                return false;
            }
            String normalized = modId.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return false;
            }
            return ignoredMods.contains(normalized);
        }

        public boolean isModIgnored(String modId) {
            return isIgnored(modId);
        }

        public boolean toggleWhitelistedModsActive() {
            modsWhitelistedEnabled = !modsWhitelistedEnabled;
            return modsWhitelistedEnabled;
        }

        public boolean toggleBlacklistedModsActive() {
            modsBlacklistedEnabled = !modsBlacklistedEnabled;
            return modsBlacklistedEnabled;
        }

        public boolean toggleRequiredModsActive() {
            modsRequiredEnabled = !modsRequiredEnabled;
            return modsRequiredEnabled;
        }

        public boolean setModConfig(String modId, String mode, String action, String warnMessage) {
            ModConfigStore.upsertModConfig(
                modConfigMap,
                requiredModsActive,
                blacklistedModsActive,
                whitelistedModsActive,
                optionalModsActive,
                modId,
                mode,
                action,
                warnMessage,
                "none",
                "kick"
            );
            return true;
        }

        public boolean removeModConfig(String modId) {
            return ModConfigStore.removeModConfig(
                modConfigMap,
                requiredModsActive,
                blacklistedModsActive,
                whitelistedModsActive,
                optionalModsActive,
                modId
            );
        }

        public ConfigTypes.ConfigState.ModConfig getModConfig(String modId) {
            if (modId == null) {
                String defaultModeStr = whitelist ? ConfigTypes.ConfigState.MODE_BLACKLISTED : ConfigTypes.ConfigState.MODE_ALLOWED;
                return new ConfigTypes.ConfigState.ModConfig(defaultModeStr, "kick", null);
            }

            String normalized = modId.toLowerCase(Locale.ROOT);
            ConfigTypes.ConfigState.ModConfig cfg = modConfigMap.get(normalized);
            if (cfg != null) return cfg;

            String defaultModeStr = whitelist ? ConfigTypes.ConfigState.MODE_BLACKLISTED : ConfigTypes.ConfigState.MODE_ALLOWED;
            return new ConfigTypes.ConfigState.ModConfig(defaultModeStr, "kick", null);
        }

        public void addAllMods(Set<String> mods, String mode, String action, String warnMessage) {
            ModConfigStore.addAllMods(
                mods,
                mode,
                action,
                warnMessage,
                modConfigMap,
                requiredModsActive,
                blacklistedModsActive,
                whitelistedModsActive,
                optionalModsActive,
                "none",
                "kick"
            );
        }
    }
    // endregion
}
