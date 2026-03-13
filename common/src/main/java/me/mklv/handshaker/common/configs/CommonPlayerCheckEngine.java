package me.mklv.handshaker.common.configs;

import me.mklv.handshaker.common.configs.ConfigRuntime.CommonConfigManagerBase;
import me.mklv.handshaker.common.configs.ConfigTypes.ActionDefinition;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.Behavior;
import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState.IntegrityMode;
import me.mklv.handshaker.common.configs.ConfigTypes.StandardMessages;
import me.mklv.handshaker.common.protocols.BedrockPlayer;
import me.mklv.handshaker.common.utils.ClientInfo;
import me.mklv.handshaker.common.utils.ModChecks.ModCheckEvaluator;
import me.mklv.handshaker.common.utils.ModChecks.ModCheckInput;
import me.mklv.handshaker.common.utils.ModChecks.ModCheckResult;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CommonPlayerCheckEngine {
    private CommonPlayerCheckEngine() {
    }

    public interface Bridge {
        void info(String message);

        void warn(String message);

        void disconnect(String message);

        void executeServerCommand(String command);

        void publishBan(String playerName, String reason, String mods);

        void publishKick(String playerName, String reason, String mods);

        boolean hasBypassPermission();
    }

    public static void checkPlayer(CommonConfigManagerBase config,
                                   UUID playerId,
                                   String playerName,
                                   ClientInfo info,
                                   boolean executeActions,
                                   boolean isTimeoutCheck,
                                   Map<String, String> knownHashes,
                                   Bridge bridge) {
        boolean bedrockPlayer = BedrockPlayer.isBedrockPlayer(
            playerId,
            playerName,
            bridge::warn
        );
        if (bedrockPlayer) {
            if (config.isAllowBedrockPlayers()) {
                return;
            }

            bridge.disconnect(config.getMessageOrDefault(
                StandardMessages.KEY_BEDROCK,
                StandardMessages.DEFAULT_BEDROCK_MESSAGE
            ));
            return;
        }

        if (bridge.hasBypassPermission()) {
            return;
        }

        boolean hasMod = info != null && !info.mods().isEmpty();

        if (config.getIntegrityMode() == IntegrityMode.SIGNED && hasMod) {
            if (info.integrityNonce() == null && isTimeoutCheck) {
                bridge.warn("Kicking " + playerName + " - mod client but no integrity data sent in SIGNED mode");
                bridge.disconnect(config.getInvalidSignatureKickMessage());
                return;
            }
            if (info.integrityNonce() != null && !info.signatureVerified()) {
                bridge.warn("Kicking " + playerName + " - integrity check FAILED in SIGNED mode");
                bridge.disconnect(config.getInvalidSignatureKickMessage());
                return;
            }
        }

        if (config.getBehavior() == Behavior.VANILLA && !hasMod) {
            return;
        }

        if (config.getBehavior() == Behavior.STRICT && !hasMod) {
            bridge.disconnect(config.getNoHandshakeKickMessage());
            return;
        }

        if (!hasMod) {
            return;
        }

        ModCheckInput input = new ModCheckInput(
            config.isWhitelist(),
            config.areModsRequiredEnabled(),
            config.areModsBlacklistedEnabled(),
            config.areModsWhitelistedEnabled(),
            config.isHashMods(),
            config.isModVersioning(),
            config.isHybridCompatibilityEnabled(),
            config.getRequiredModpackHashes(),
            knownHashes,
            config.getIgnoredMods(),
            config.getWhitelistedMods(),
            config.getOptionalMods(),
            config.getBlacklistedMods(),
            config.getRequiredMods(),
            config.getModConfigMap(),
            config.getKickMessage(),
            config.getMessageOrDefault(StandardMessages.KEY_KICK_SPOOFED_MOD, StandardMessages.KICK_SPOOFED_MOD),
            config.getMissingWhitelistModMessage(),
            config.getMessageOrDefault(StandardMessages.KEY_MODPACK_HASH_MISMATCH, StandardMessages.MODPACK_HASH_MISMATCH)
        );
        ModCheckResult result = ModCheckEvaluator.evaluate(input, info.mods());

        if (result.isViolation()) {
            if (result.isBlacklistedViolation()) {
                String actionName = result.getActionName() != null
                    ? result.getActionName().toLowerCase(Locale.ROOT)
                    : "kick";
                ActionDefinition actionDef = config.getAction(actionName);
                if (actionDef != null && !actionDef.isEmpty()) {
                    if ("ban".equalsIgnoreCase(actionName)) {
                        String banReason = result.getMessage() != null ? result.getMessage() : "Action 'ban' executed";
                        bridge.publishBan(playerName, banReason, String.join(", ", result.getMods()));
                    }
                    if (actionDef.shouldLog()) {
                        bridge.info("Executing action '" + actionName + "' for player " + playerName +
                            " (blacklisted mods: " + result.getMods() + ")");
                    }

                    executeCommands(
                        config,
                        actionDef,
                        playerName,
                        String.join(", ", result.getMods()),
                        bridge
                    );
                }
            }

            if (result.getMessage() != null) {
                boolean isBanAction = "ban".equalsIgnoreCase(result.getActionName());
                if (!isBanAction) {
                    bridge.publishKick(playerName, result.getMessage(), String.join(", ", result.getMods()));
                }
                String disconnectMsg = isBanAction
                    ? MessagePlaceholderExpander.expand(
                        config.getMessageOrDefault(StandardMessages.KEY_BAN, StandardMessages.DEFAULT_BAN_MESSAGE),
                        playerName,
                        String.join(", ", result.getMods()),
                        config.getMessages()
                    )
                    : result.getMessage();
                bridge.disconnect(disconnectMsg);
            }
            return;
        }

        if (executeActions && config.areModsWhitelistedEnabled() && result.hasAllowedActions()) {
            for (Map.Entry<String, String> entry : result.getAllowedActionsByMod().entrySet()) {
                String allowedMod = entry.getKey();
                String actionName = entry.getValue();
                ActionDefinition actionDef = config.getAction(actionName.toLowerCase(Locale.ROOT));
                if (actionDef != null && !actionDef.isEmpty()) {
                    if (actionDef.shouldLog()) {
                        bridge.info("Executing action '" + actionName + "' for player " + playerName +
                            " (allowed mod: " + allowedMod + ")");
                    }
                    executeCommands(config, actionDef, playerName, allowedMod, bridge);
                }
            }
        }
    }

    private static void executeCommands(CommonConfigManagerBase config,
                                        ActionDefinition actionDef,
                                        String playerName,
                                        String modText,
                                        Bridge bridge) {
        for (String command : actionDef.getCommands()) {
            String expandedCommand = MessagePlaceholderExpander.expand(
                command,
                playerName,
                modText,
                config.getMessages()
            );
            try {
                bridge.executeServerCommand(expandedCommand);
            } catch (Exception e) {
                bridge.warn("Failed to execute action command '" + expandedCommand + "': " + e.getMessage());
            }
        }
    }
}