package me.mklv.handshaker.common.configs;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigTypes {
    private ConfigTypes() {
    }

    public static class ActionDefinition {
        private final String name;
        private final List<String> commands;
        private final boolean shouldLog;

        public ActionDefinition(String name) {
            this(name, new ArrayList<>(), false);
        }

        public ActionDefinition(String name, List<String> commands) {
            this(name, commands, false);
        }

        public ActionDefinition(String name, List<String> commands, boolean shouldLog) {
            this.name = name;
            this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
            this.shouldLog = shouldLog;
        }

        public String getName() {
            return name;
        }

        public List<String> getCommands() {
            return commands;
        }

        public boolean shouldLog() {
            return shouldLog;
        }

        public void addCommand(String command) {
            this.commands.add(command);
        }

        public boolean isEmpty() {
            return commands.isEmpty();
        }

        public int getCommandCount() {
            return commands.size();
        }
    }

    public static final class StandardMessages {
        private StandardMessages() {
        }

        public static final String KEY_KICK = "kick";
        public static final String KEY_NO_HANDSHAKE = "no-handshake";
        public static final String KEY_MISSING_WHITELIST = "missing-whitelist";
        public static final String KEY_INVALID_SIGNATURE = "invalid-signature";
        public static final String KEY_OUTDATED_CLIENT = "outdated-client";
        public static final String KEY_HANDSHAKE_CORRUPTED = "handshake-corrupted";
        public static final String KEY_HANDSHAKE_EMPTY_MOD_LIST = "handshake-empty-mod-list";
        public static final String KEY_HANDSHAKE_HASH_MISMATCH = "handshake-hash-mismatch";
        public static final String KEY_HANDSHAKE_MISSING_HASH = "handshake-missing-hash";
        public static final String KEY_HANDSHAKE_MISSING_JAR_HASH = "handshake-missing-jar-hash";
        public static final String KEY_HANDSHAKE_MISSING_NONCE = "handshake-missing-nonce";
        public static final String KEY_HANDSHAKE_MISSING_SIGNATURE = "handshake-missing-signature";
        public static final String KEY_HANDSHAKE_REPLAY = "handshake-replay";
        public static final String KEY_VELTON_FAILED = "velton-verification-failed";

        public static final String DEFAULT_KICK_MESSAGE =
            "You are using a blacklisted mod: {mod}. Please remove it to join this server.";
        public static final String DEFAULT_NO_HANDSHAKE_MESSAGE =
            "To connect to this server please download 'Hand-shaker' mod.";
        public static final String DEFAULT_MISSING_WHITELIST_MESSAGE =
            "You are missing required mods: {mod}. Please install them to join this server.";
        public static final String DEFAULT_INVALID_SIGNATURE_MESSAGE =
            "Invalid client signature. Please use the official client.";
        public static final String DEFAULT_OUTDATED_CLIENT_MESSAGE =
            "Your HandShaker client is outdated. Please update to the latest version.";

        public static final String HANDSHAKE_CORRUPTED = "Corrupted handshake data";
        public static final String HANDSHAKE_EMPTY_MOD_LIST = "Invalid handshake: empty mod list";
        public static final String HANDSHAKE_HASH_MISMATCH = "Invalid handshake: hash mismatch";
        public static final String HANDSHAKE_MISSING_HASH = "Invalid handshake: missing hash";
        public static final String HANDSHAKE_MISSING_JAR_HASH = "Invalid handshake: missing jar hash";
        public static final String HANDSHAKE_MISSING_NONCE = "Invalid handshake: missing nonce";
        public static final String HANDSHAKE_MISSING_SIGNATURE = "Invalid handshake: missing signature";
        public static final String HANDSHAKE_REPLAY = "Replay attack detected";

        public static final String VELTON_VERIFICATION_FAILED = "Anti-cheat verification failed";
    }

    public record ModEntry(String modId, String version, String hash) {
        public static ModEntry parse(String raw) {
            if (raw == null) {
                return null;
            }

            String trimmed = raw.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                return null;
            }

            String[] parts = trimmed.split(":", 3);
            String modId = normalizePart(parts[0]);
            if (modId == null) {
                return null;
            }

            String version = parts.length > 1 ? normalizePart(parts[1]) : null;
            String hash = parts.length > 2 ? normalizePart(parts[2]) : null;
            return new ModEntry(modId, version, hash);
        }

        public String toRuleKey(boolean versioningEnabled) {
            if (!versioningEnabled || version == null) {
                return modId;
            }
            return modId + ":" + version;
        }

        public String toDisplayKey() {
            if (version == null) {
                return modId;
            }
            return modId + ":" + version;
        }

        private static String normalizePart(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed)) {
                return null;
            }
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    public static final class ConfigState {
        public static final String MODE_REQUIRED = "required";
        public static final String MODE_BLACKLISTED = "blacklisted";
        public static final String MODE_ALLOWED = "allowed";
        public static final String MODE_WHITELISTED = "whitelisted";

        private ConfigState() {
        }

        public static String normalizeMode(String mode) {
            if (mode == null) {
                return MODE_ALLOWED;
            }
            return switch (mode.trim().toLowerCase(Locale.ROOT)) {
                case MODE_REQUIRED -> MODE_REQUIRED;
                case MODE_BLACKLISTED -> MODE_BLACKLISTED;
                case MODE_WHITELISTED -> MODE_WHITELISTED;
                case MODE_ALLOWED -> MODE_ALLOWED;
                default -> MODE_ALLOWED;
            };
        }

        public enum Behavior { STRICT, VANILLA }
        public enum IntegrityMode { SIGNED, DEV }

        public enum Action {
            KICK, BAN;

            public static Action fromString(String str) {
                if (str == null) return KICK;
                return switch (str.toUpperCase(Locale.ROOT)) {
                    case "BAN" -> BAN;
                    default -> KICK;
                };
            }
        }

        public static class ModConfig {
            private String mode;
            private String action;
            private String warnMessage;

            public ModConfig(String mode, String action, String warnMessage) {
                this.mode = mode != null ? mode : MODE_ALLOWED;
                this.action = action != null ? action : "kick";
                this.warnMessage = warnMessage;
            }

            public String getMode() { return mode; }
            public void setMode(String mode) { this.mode = mode; }
            public Action getAction() { return Action.fromString(action); }
            public String getActionName() { return action; }
            public void setAction(String action) { this.action = action; }
            public String getWarnMessage() { return warnMessage; }
            public void setWarnMessage(String warnMessage) { this.warnMessage = warnMessage; }

            public boolean isRequired() { return MODE_REQUIRED.equalsIgnoreCase(mode); }
            public boolean isBlacklisted() { return MODE_BLACKLISTED.equalsIgnoreCase(mode); }
            public boolean isAllowed() { return MODE_ALLOWED.equalsIgnoreCase(mode); }
        }
    }

    public static final class ConfigLoadOptions {
        private final boolean defaultModsWhitelistedEnabled;
        private final boolean loadWhitelistedWhenDisabled;
        private final boolean createWhitelistedFileWhenEnabled;
        private final String defaultWhitelistedAction;
        private final boolean includeEmptyActions;

        public ConfigLoadOptions(
            boolean defaultModsWhitelistedEnabled,
            boolean loadWhitelistedWhenDisabled,
            boolean createWhitelistedFileWhenEnabled,
            String defaultWhitelistedAction,
            boolean includeEmptyActions
        ) {
            this.defaultModsWhitelistedEnabled = defaultModsWhitelistedEnabled;
            this.loadWhitelistedWhenDisabled = loadWhitelistedWhenDisabled;
            this.createWhitelistedFileWhenEnabled = createWhitelistedFileWhenEnabled;
            this.defaultWhitelistedAction = defaultWhitelistedAction != null ? defaultWhitelistedAction : "none";
            this.includeEmptyActions = includeEmptyActions;
        }

        public boolean isDefaultModsWhitelistedEnabled() {
            return defaultModsWhitelistedEnabled;
        }

        public boolean isLoadWhitelistedWhenDisabled() {
            return loadWhitelistedWhenDisabled;
        }

        public boolean isCreateWhitelistedFileWhenEnabled() {
            return createWhitelistedFileWhenEnabled;
        }

        public String getDefaultWhitelistedAction() {
            return defaultWhitelistedAction;
        }

        public boolean isIncludeEmptyActions() {
            return includeEmptyActions;
        }
    }

    public static final class ConfigLoadResult {
        private ConfigState.Behavior behavior = ConfigState.Behavior.STRICT;
        private ConfigState.IntegrityMode integrityMode = ConfigState.IntegrityMode.SIGNED;
        private String kickMessage = StandardMessages.DEFAULT_KICK_MESSAGE;
        private String noHandshakeKickMessage = StandardMessages.DEFAULT_NO_HANDSHAKE_MESSAGE;
        private String missingWhitelistModMessage = StandardMessages.DEFAULT_MISSING_WHITELIST_MESSAGE;
        private String invalidSignatureKickMessage = StandardMessages.DEFAULT_INVALID_SIGNATURE_MESSAGE;
        private boolean allowBedrockPlayers = false;
        private boolean playerdbEnabled = false;
        private boolean modsRequiredEnabled = true;
        private boolean modsBlacklistedEnabled = true;
        private boolean modsWhitelistedEnabled = true;
        private boolean hashMods = true;
        private boolean modVersioning = true;
        private boolean whitelist = false;
        private int handshakeTimeoutSeconds = 5;

        private final Map<String, String> messages = new LinkedHashMap<>();
        private final Map<String, ConfigState.ModConfig> modConfigMap = new LinkedHashMap<>();
        private final Set<String> ignoredMods = new LinkedHashSet<>();
        private final Set<String> whitelistedModsActive = new LinkedHashSet<>();
        private final Set<String> blacklistedModsActive = new LinkedHashSet<>();
        private final Set<String> requiredModsActive = new LinkedHashSet<>();
        private final Set<String> optionalModsActive = new LinkedHashSet<>();
        private final Map<String, ActionDefinition> actionsMap = new LinkedHashMap<>();

        public ConfigState.Behavior getBehavior() {
            return behavior;
        }

        public void setBehavior(ConfigState.Behavior behavior) {
            this.behavior = behavior;
        }

        public ConfigState.IntegrityMode getIntegrityMode() {
            return integrityMode;
        }

        public void setIntegrityMode(ConfigState.IntegrityMode integrityMode) {
            this.integrityMode = integrityMode;
        }

        public String getKickMessage() {
            return kickMessage;
        }

        public void setKickMessage(String kickMessage) {
            this.kickMessage = kickMessage;
        }

        public String getNoHandshakeKickMessage() {
            return noHandshakeKickMessage;
        }

        public void setNoHandshakeKickMessage(String noHandshakeKickMessage) {
            this.noHandshakeKickMessage = noHandshakeKickMessage;
        }

        public String getMissingWhitelistModMessage() {
            return missingWhitelistModMessage;
        }

        public void setMissingWhitelistModMessage(String missingWhitelistModMessage) {
            this.missingWhitelistModMessage = missingWhitelistModMessage;
        }

        public String getInvalidSignatureKickMessage() {
            return invalidSignatureKickMessage;
        }

        public void setInvalidSignatureKickMessage(String invalidSignatureKickMessage) {
            this.invalidSignatureKickMessage = invalidSignatureKickMessage;
        }

        public boolean isAllowBedrockPlayers() {
            return allowBedrockPlayers;
        }

        public void setAllowBedrockPlayers(boolean allowBedrockPlayers) {
            this.allowBedrockPlayers = allowBedrockPlayers;
        }

        public boolean isPlayerdbEnabled() {
            return playerdbEnabled;
        }

        public void setPlayerdbEnabled(boolean playerdbEnabled) {
            this.playerdbEnabled = playerdbEnabled;
        }

        public boolean areModsRequiredEnabled() {
            return modsRequiredEnabled;
        }

        public void setModsRequiredEnabled(boolean modsRequiredEnabled) {
            this.modsRequiredEnabled = modsRequiredEnabled;
        }

        public boolean areModsBlacklistedEnabled() {
            return modsBlacklistedEnabled;
        }

        public void setModsBlacklistedEnabled(boolean modsBlacklistedEnabled) {
            this.modsBlacklistedEnabled = modsBlacklistedEnabled;
        }

        public boolean areModsWhitelistedEnabled() {
            return modsWhitelistedEnabled;
        }

        public void setModsWhitelistedEnabled(boolean modsWhitelistedEnabled) {
            this.modsWhitelistedEnabled = modsWhitelistedEnabled;
        }

        public boolean isHashMods() {
            return hashMods;
        }

        public void setHashMods(boolean hashMods) {
            this.hashMods = hashMods;
        }

        public boolean isModVersioning() {
            return modVersioning;
        }

        public void setModVersioning(boolean modVersioning) {
            this.modVersioning = modVersioning;
        }

        public boolean isWhitelist() {
            return whitelist;
        }

        public void setWhitelist(boolean whitelist) {
            this.whitelist = whitelist;
        }

        public int getHandshakeTimeoutSeconds() {
            return handshakeTimeoutSeconds;
        }

        public void setHandshakeTimeoutSeconds(int handshakeTimeoutSeconds) {
            this.handshakeTimeoutSeconds = handshakeTimeoutSeconds;
        }

        public Map<String, String> getMessages() {
            return messages;
        }

        public Map<String, ConfigState.ModConfig> getModConfigMap() {
            return modConfigMap;
        }

        public Set<String> getIgnoredMods() {
            return ignoredMods;
        }

        public Set<String> getWhitelistedModsActive() {
            return whitelistedModsActive;
        }

        public Set<String> getBlacklistedModsActive() {
            return blacklistedModsActive;
        }

        public Set<String> getRequiredModsActive() {
            return requiredModsActive;
        }

        public Set<String> getOptionalModsActive() {
            return optionalModsActive;
        }

        public Map<String, ActionDefinition> getActionsMap() {
            return actionsMap;
        }
    }
}
