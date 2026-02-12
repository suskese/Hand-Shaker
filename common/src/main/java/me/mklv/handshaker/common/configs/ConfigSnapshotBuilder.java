package me.mklv.handshaker.common.configs;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class ConfigSnapshotBuilder {
    private ConfigSnapshotBuilder() {
    }

    public static ConfigLoadResult build(ConfigState.Behavior behavior,
                                         ConfigState.IntegrityMode integrityMode,
                                         String kickMessage,
                                         String noHandshakeKickMessage,
                                         String missingWhitelistModMessage,
                                         String invalidSignatureKickMessage,
                                         boolean allowBedrockPlayers,
                                         boolean playerdbEnabled,
                                         boolean modsRequiredEnabled,
                                         boolean modsBlacklistedEnabled,
                                         boolean modsWhitelistedEnabled,
                                         boolean whitelist,
                                         int handshakeTimeoutSeconds,
                                         Map<String, String> messages,
                                         Map<String, ConfigState.ModConfig> modConfigMap,
                                         Set<String> ignoredMods,
                                         Set<String> whitelistedModsActive,
                                         Set<String> blacklistedModsActive,
                                         Set<String> requiredModsActive,
                                         Set<String> optionalModsActive,
                                         Map<String, ActionDefinition> actionsMap) {
        ConfigLoadResult snapshot = new ConfigLoadResult();
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
