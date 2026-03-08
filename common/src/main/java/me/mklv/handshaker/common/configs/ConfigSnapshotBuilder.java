package me.mklv.handshaker.common.configs;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public final class ConfigSnapshotBuilder {
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
                                                     String requiredModpackHash,
                                                     String defaultAction,
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
        snapshot.setForceHandshakerMod(forceHandshakerMod);
        snapshot.setModernCompatibility(modernCompatibility);
        snapshot.setHybridCompatibility(hybridCompatibility);
        snapshot.setLegacyCompatibility(legacyCompatibility);
        snapshot.setUnsignedCompatibility(unsignedCompatibility);
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
        snapshot.setRuntimeCache(runtimeCache);
        snapshot.setModVersioning(modVersioning);
        snapshot.setRequiredModpackHash(requiredModpackHash);
        snapshot.setDefaultAction(defaultAction);
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
