package me.mklv.handshaker.common.configs;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ConfigLoadResult {
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
