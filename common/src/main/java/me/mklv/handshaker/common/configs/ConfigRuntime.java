package me.mklv.handshaker.common.configs;

import me.mklv.handshaker.common.utils.ModCache;
import me.mklv.handshaker.common.utils.WildcardMatcher;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigRuntime {
    private ConfigRuntime() {
    }

    // region ConfigSnapshotBuilder
    public static final class ConfigSnapshotBuilder {
        private ConfigSnapshotBuilder() {
        }

        public static ConfigTypes.ConfigLoadResult build(ConfigTypes.ConfigState.Behavior behavior,
                                                         ConfigTypes.ConfigState.IntegrityMode integrityMode,
                                                         boolean forceHandshakerMod,
                                                         boolean modernCompatibility,
                                                         boolean hybridCompatibility,
                                                         boolean legacyCompatibility,
                                                         boolean unsignedCompatibility,
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
                                                         boolean runtimeCache,
                                                         boolean modVersioning,
                                                         Set<String> requiredModpackHashes,
                                                         String defaultAction,
                                                         boolean whitelist,
                                                         int handshakeTimeoutSeconds,
                                                         int rateLimitPerMinute,
                                                         boolean diagnosticCommandEnabled,
                                                         boolean exportCommandEnabled,
                                                         boolean asyncDatabaseOperations,
                                                         int databasePoolSize,
                                                         long databaseIdleTimeoutMs,
                                                         long databaseMaxLifetimeMs,
                                                         int deleteHistoryDays,
                                                         boolean payloadCompressionEnabled,
                                                         boolean restApiEnabled,
                                                         int restApiPort,
                                                         boolean webhookEnabled,
                                                         String webhookUrl,
                                                         boolean webhookNotifyOnBan,
                                                         boolean webhookNotifyOnKick,
                                                         Map<String, String> messages,
                                                         Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                                         Set<String> ignoredMods,
                                                         Set<String> whitelistedModsActive,
                                                         Set<String> blacklistedModsActive,
                                                         Set<String> requiredModsActive,
                                                         Set<String> optionalModsActive,
                                                         Map<String, ConfigTypes.ActionDefinition> actionsMap) {
            return me.mklv.handshaker.common.configs.ConfigSnapshotBuilder.build(
                behavior,
                integrityMode,
                forceHandshakerMod,
                modernCompatibility,
                hybridCompatibility,
                legacyCompatibility,
                unsignedCompatibility,
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
                runtimeCache,
                modVersioning,
                requiredModpackHashes,
                defaultAction,
                whitelist,
                handshakeTimeoutSeconds,
                rateLimitPerMinute,
                diagnosticCommandEnabled,
                exportCommandEnabled,
                asyncDatabaseOperations,
                databasePoolSize,
                databaseIdleTimeoutMs,
                databaseMaxLifetimeMs,
                deleteHistoryDays,
                payloadCompressionEnabled,
                restApiEnabled,
                restApiPort,
                webhookEnabled,
                webhookUrl,
                webhookNotifyOnBan,
                webhookNotifyOnKick,
                messages,
                modConfigMap,
                ignoredMods,
                whitelistedModsActive,
                blacklistedModsActive,
                requiredModsActive,
                optionalModsActive,
                actionsMap
            );
        }
    }
    // endregion

    // region MessagePlaceholderExpander
    public static final class MessagePlaceholderExpander {
        private MessagePlaceholderExpander() {
        }

        public static String expand(String command,
                                    String playerName,
                                    String modText,
                                    Map<String, String> messages) {
            return me.mklv.handshaker.common.configs.MessagePlaceholderExpander.expand(command, playerName, modText, messages);
        }
    }
    // endregion

    // region ModConfigStore
    public static final class ModConfigStore {
        private ModConfigStore() {
        }

        public static String normalizeModId(String modId) {
            return me.mklv.handshaker.common.configs.ModConfigStore.normalizeModId(modId);
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
            me.mklv.handshaker.common.configs.ModConfigStore.upsertModConfig(
                modConfigMap,
                requiredModsActive,
                blacklistedModsActive,
                whitelistedModsActive,
                optionalModsActive,
                modId,
                mode,
                action,
                warnMessage,
                defaultAllowedAction,
                defaultOtherAction
            );
        }

        public static boolean removeModConfig(Map<String, ConfigTypes.ConfigState.ModConfig> modConfigMap,
                                              Set<String> requiredModsActive,
                                              Set<String> blacklistedModsActive,
                                              Set<String> whitelistedModsActive,
                                              Set<String> optionalModsActive,
                                              String modId) {
            return me.mklv.handshaker.common.configs.ModConfigStore.removeModConfig(
                modConfigMap,
                requiredModsActive,
                blacklistedModsActive,
                whitelistedModsActive,
                optionalModsActive,
                modId
            );
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
            me.mklv.handshaker.common.configs.ModConfigStore.addAllMods(
                mods,
                mode,
                action,
                warnMessage,
                modConfigMap,
                requiredModsActive,
                blacklistedModsActive,
                whitelistedModsActive,
                optionalModsActive,
                defaultAllowedAction,
                defaultOtherAction
            );
        }

        public static String resolveActionDefault(String mode,
                                                  String action,
                                                  String defaultAllowedAction,
                                                  String defaultOtherAction) {
            return me.mklv.handshaker.common.configs.ModConfigStore.resolveActionDefault(
                mode,
                action,
                defaultAllowedAction,
                defaultOtherAction
            );
        }
    }
    // endregion

    // region CommonConfigManagerBase
    public abstract static class CommonConfigManagerBase {
        protected boolean debug = false;
        protected ConfigTypes.ConfigState.Behavior behavior = ConfigTypes.ConfigState.Behavior.STRICT;
        protected ConfigTypes.ConfigState.IntegrityMode integrityMode = ConfigTypes.ConfigState.IntegrityMode.SIGNED;
        protected boolean forceHandshakerMod = true;
        protected boolean modernCompatibility = true;
        protected boolean hybridCompatibility = false;
        protected boolean legacyCompatibility = false;
        protected boolean unsignedCompatibility = false;
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
        protected boolean runtimeCache = false;
        protected boolean modVersioning = true;
        protected final Set<String> requiredModpackHashes = new LinkedHashSet<>();
        protected String defaultAction = "kick";
        protected int rateLimitPerMinute = 10;
        protected boolean diagnosticCommandEnabled = true;
        protected boolean exportCommandEnabled = true;
        protected boolean asyncDatabaseOperations = true;
        protected int databasePoolSize = 15;
        protected long databaseIdleTimeoutMs = 300_000L;
        protected long databaseMaxLifetimeMs = 1_800_000L;
        protected int deleteHistoryDays = 0;
        protected boolean payloadCompressionEnabled = true;
        protected boolean restApiEnabled = false;
        protected int restApiPort = 8080;
        protected boolean webhookEnabled = false;
        protected String webhookUrl = "";
        protected boolean webhookNotifyOnBan = true;
        protected boolean webhookNotifyOnKick = false;

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
            if (result != null && result.isRewriteToV7()) {
                ConfigWriter.writeAll(configDir, logger, result);
            }
        }

        protected final void applyLoadResult(ConfigTypes.ConfigLoadResult result) {
            if (result == null) {
                return;
            }

            debug = result.isDebug();

            behavior = result.getBehavior();
            integrityMode = result.getIntegrityMode();
            forceHandshakerMod = result.isForceHandshakerMod();
            modernCompatibility = result.isModernCompatibility();
            hybridCompatibility = result.isHybridCompatibility();
            legacyCompatibility = result.isLegacyCompatibility();
            unsignedCompatibility = result.isUnsignedCompatibility();
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
            runtimeCache = result.isRuntimeCache();
            modVersioning = result.isModVersioning();
            requiredModpackHashes.clear();
            requiredModpackHashes.addAll(result.getRequiredModpackHashes());
            defaultAction = result.getDefaultAction() != null && !result.getDefaultAction().isBlank()
                ? result.getDefaultAction().toLowerCase(Locale.ROOT)
                : "kick";
            whitelist = result.isWhitelist();
            rateLimitPerMinute = result.getRateLimitPerMinute();
            diagnosticCommandEnabled = result.isDiagnosticCommandEnabled();
            exportCommandEnabled = result.isExportCommandEnabled();
            asyncDatabaseOperations = result.isAsyncDatabaseOperations();
            databasePoolSize = result.getDatabasePoolSize();
            databaseIdleTimeoutMs = result.getDatabaseIdleTimeoutMs();
            databaseMaxLifetimeMs = result.getDatabaseMaxLifetimeMs();
            deleteHistoryDays = result.getDeleteHistoryDays();
            payloadCompressionEnabled = result.isPayloadCompressionEnabled();
            restApiEnabled = result.isRestApiEnabled();
            restApiPort = result.getRestApiPort();
            webhookEnabled = result.isWebhookEnabled();
            webhookUrl = result.getWebhookUrl();
            webhookNotifyOnBan = result.isWebhookNotifyOnBan();
            webhookNotifyOnKick = result.isWebhookNotifyOnKick();

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
                forceHandshakerMod,
                modernCompatibility,
                hybridCompatibility,
                legacyCompatibility,
                unsignedCompatibility,
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
                runtimeCache,
                modVersioning,
                requiredModpackHashes,
                defaultAction,
                whitelist,
                handshakeTimeoutSeconds,
                rateLimitPerMinute,
                diagnosticCommandEnabled,
                exportCommandEnabled,
                asyncDatabaseOperations,
                databasePoolSize,
                databaseIdleTimeoutMs,
                databaseMaxLifetimeMs,
                deleteHistoryDays,
                payloadCompressionEnabled,
                restApiEnabled,
                restApiPort,
                webhookEnabled,
                webhookUrl,
                webhookNotifyOnBan,
                webhookNotifyOnKick,
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
            return forceHandshakerMod ? ConfigTypes.ConfigState.Behavior.STRICT : ConfigTypes.ConfigState.Behavior.VANILLA;
        }

        public ConfigTypes.ConfigState.IntegrityMode getIntegrityMode() {
            return unsignedCompatibility ? ConfigTypes.ConfigState.IntegrityMode.DEV : ConfigTypes.ConfigState.IntegrityMode.SIGNED;
        }

        public boolean isForceHandshakerMod() {
            return forceHandshakerMod;
        }

        public boolean isModernCompatibilityEnabled() {
            return modernCompatibility;
        }

        public boolean isHybridCompatibilityEnabled() {
            return hybridCompatibility;
        }

        public boolean isLegacyCompatibilityEnabled() {
            return legacyCompatibility;
        }

        public boolean isUnsignedCompatibilityEnabled() {
            return unsignedCompatibility;
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

        public boolean isRuntimeCache() {
            return runtimeCache;
        }

        public boolean isModVersioning() {
            return modVersioning;
        }

        public Set<String> getRequiredModpackHashes() {
            return Collections.unmodifiableSet(requiredModpackHashes);
        }

        public String getRequiredModpackHash() {
            return requiredModpackHashes.isEmpty() ? null : requiredModpackHashes.iterator().next();
        }

        public String getDefaultAction() {
            return defaultAction;
        }

        public int getRateLimitPerMinute() {
            return rateLimitPerMinute;
        }

        public boolean isDiagnosticCommandEnabled() {
            return diagnosticCommandEnabled;
        }

        public boolean isExportCommandEnabled() {
            return exportCommandEnabled;
        }

        public boolean isAsyncDatabaseOperations() {
            return asyncDatabaseOperations;
        }

        public int getDatabasePoolSize() {
            return databasePoolSize;
        }

        public long getDatabaseIdleTimeoutMs() {
            return databaseIdleTimeoutMs;
        }

        public long getDatabaseMaxLifetimeMs() {
            return databaseMaxLifetimeMs;
        }

        public int getDeleteHistoryDays() {
            return deleteHistoryDays;
        }

        public boolean isPayloadCompressionEnabled() {
            return payloadCompressionEnabled;
        }

        public boolean isRestApiEnabled() {
            return restApiEnabled;
        }

        public int getRestApiPort() {
            return restApiPort;
        }

        public boolean isWebhookEnabled() {
            return webhookEnabled;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public boolean isWebhookNotifyOnBan() {
            return webhookNotifyOnBan;
        }

        public boolean isWebhookNotifyOnKick() {
            return webhookNotifyOnKick;
        }

        public String getDefaultActionForMode(String mode) {
            String normalizedMode = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
            if (ConfigTypes.ConfigState.MODE_BLACKLISTED.equals(normalizedMode)) {
                return defaultAction;
            }
            return "none";
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

        public Map<String, String> getMessages() {
            return Collections.unmodifiableMap(messagesMap);
        }

        public void setBehavior(String value) {
            this.forceHandshakerMod = value != null && value.equalsIgnoreCase("STRICT");
            this.behavior = forceHandshakerMod ? ConfigTypes.ConfigState.Behavior.STRICT : ConfigTypes.ConfigState.Behavior.VANILLA;
        }

        public void setIntegrityMode(String value) {
            this.unsignedCompatibility = value != null && value.equalsIgnoreCase("DEV");
            this.modernCompatibility = true;
            this.integrityMode = unsignedCompatibility ? ConfigTypes.ConfigState.IntegrityMode.DEV : ConfigTypes.ConfigState.IntegrityMode.SIGNED;
        }

        public void setForceHandshakerMod(boolean forceHandshakerMod) {
            this.forceHandshakerMod = forceHandshakerMod;
            this.behavior = forceHandshakerMod ? ConfigTypes.ConfigState.Behavior.STRICT : ConfigTypes.ConfigState.Behavior.VANILLA;
        }

        public void setModernCompatibilityEnabled(boolean enabled) {
            this.modernCompatibility = enabled;
        }

        public void setHybridCompatibilityEnabled(boolean enabled) {
            this.hybridCompatibility = enabled;
        }

        public void setLegacyCompatibilityEnabled(boolean enabled) {
            this.legacyCompatibility = enabled;
        }

        public void setUnsignedCompatibilityEnabled(boolean enabled) {
            this.unsignedCompatibility = enabled;
            this.integrityMode = enabled ? ConfigTypes.ConfigState.IntegrityMode.DEV : ConfigTypes.ConfigState.IntegrityMode.SIGNED;
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

        public void setDefaultAction(String actionName) {
            if (actionName == null || actionName.isBlank()) {
                this.defaultAction = "kick";
                return;
            }
            this.defaultAction = actionName.trim().toLowerCase(Locale.ROOT);
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
            ModCache.invalidate();
        }

        public void setRuntimeCache(boolean enabled) {
            this.runtimeCache = enabled;
            ModCache.invalidate();
        }

        public void setModVersioning(boolean enabled) {
            this.modVersioning = enabled;
            ModCache.invalidate();
        }

        public void setRequiredModpackHashes(Set<String> hashes) {
            this.requiredModpackHashes.clear();
            if (hashes == null) {
                return;
            }

            for (String hash : hashes) {
                if (hash == null) {
                    continue;
                }
                String normalized = hash.trim().toLowerCase(Locale.ROOT);
                if (normalized.matches("[0-9a-f]{64}")) {
                    this.requiredModpackHashes.add(normalized);
                }
            }
        }

        public void setRequiredModpackHash(String hash) {
            this.requiredModpackHashes.clear();
            if (hash == null) {
                return;
            }
            String normalized = hash.trim().toLowerCase(Locale.ROOT);
            if (normalized.matches("[0-9a-f]{64}")) {
                this.requiredModpackHashes.add(normalized);
            }
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
            if (ignoredMods.contains(normalized)) {
                return true;
            }

            ConfigTypes.ModEntry entry = ConfigTypes.ModEntry.parse(normalized);
            String modOnly = entry != null ? entry.modId() : normalized;
            for (String ignored : ignoredMods) {
                if (!WildcardMatcher.containsWildcard(ignored)) {
                    continue;
                }
                if (WildcardMatcher.matches(ignored, normalized) || WildcardMatcher.matches(ignored, modOnly)) {
                    return true;
                }
            }
            return false;
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
                defaultAction,
                defaultAction
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
                return new ConfigTypes.ConfigState.ModConfig(defaultModeStr, defaultAction, null);
            }

            String normalized = modId.toLowerCase(Locale.ROOT);
            ConfigTypes.ConfigState.ModConfig cfg = modConfigMap.get(normalized);
            if (cfg != null) return cfg;

            ConfigTypes.ModEntry entry = ConfigTypes.ModEntry.parse(normalized);
            String modOnly = entry != null ? entry.modId() : normalized;
            cfg = modConfigMap.get(modOnly);
            if (cfg != null) return cfg;

            for (Map.Entry<String, ConfigTypes.ConfigState.ModConfig> candidate : modConfigMap.entrySet()) {
                String ruleKey = candidate.getKey();
                if (!WildcardMatcher.containsWildcard(ruleKey)) {
                    continue;
                }
                ConfigTypes.ModEntry rule = ConfigTypes.ModEntry.parse(ruleKey);
                if (rule == null || rule.modId() == null) {
                    continue;
                }
                if (WildcardMatcher.matches(rule.modId(), modOnly)) {
                    return candidate.getValue();
                }
            }

            String defaultModeStr = whitelist ? ConfigTypes.ConfigState.MODE_BLACKLISTED : ConfigTypes.ConfigState.MODE_ALLOWED;
            return new ConfigTypes.ConfigState.ModConfig(defaultModeStr, defaultAction, null);
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
                defaultAction,
                defaultAction
            );
        }
    }
    // endregion
}
