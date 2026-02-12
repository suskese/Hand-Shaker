package me.mklv.handshaker.common.configs;

import java.util.Map;
import java.util.Set;

public final class ModCheckInput {
    private final boolean whitelist;
    private final boolean modsRequiredEnabled;
    private final boolean modsBlacklistedEnabled;
    private final boolean modsWhitelistedEnabled;
    private final Set<String> ignoredMods;
    private final Set<String> whitelistedModsActive;
    private final Set<String> optionalModsActive;
    private final Set<String> blacklistedModsActive;
    private final Set<String> requiredModsActive;
    private final Map<String, ConfigState.ModConfig> modConfigMap;
    private final String kickMessage;
    private final String missingWhitelistModMessage;

    public ModCheckInput(boolean whitelist,
                         boolean modsRequiredEnabled,
                         boolean modsBlacklistedEnabled,
                         boolean modsWhitelistedEnabled,
                         Set<String> ignoredMods,
                         Set<String> whitelistedModsActive,
                         Set<String> optionalModsActive,
                         Set<String> blacklistedModsActive,
                         Set<String> requiredModsActive,
                         Map<String, ConfigState.ModConfig> modConfigMap,
                         String kickMessage,
                         String missingWhitelistModMessage) {
        this.whitelist = whitelist;
        this.modsRequiredEnabled = modsRequiredEnabled;
        this.modsBlacklistedEnabled = modsBlacklistedEnabled;
        this.modsWhitelistedEnabled = modsWhitelistedEnabled;
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

    public Map<String, ConfigState.ModConfig> getModConfigMap() {
        return modConfigMap;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public String getMissingWhitelistModMessage() {
        return missingWhitelistModMessage;
    }
}
