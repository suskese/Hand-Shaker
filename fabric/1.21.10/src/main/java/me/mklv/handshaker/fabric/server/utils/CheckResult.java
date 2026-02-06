package me.mklv.handshaker.fabric.server.utils;

import java.util.Set;

/**
 * Represents the result of checking a player's mods
 */
public class CheckResult {
    private final String kickMessage;
    private final String actionName;
    private final Set<String> triggeredMods;
    private final boolean isRequired;
    private final boolean isBlacklisted;

    public CheckResult(String kickMessage, String actionName, Set<String> triggeredMods, boolean isRequired, boolean isBlacklisted) {
        this.kickMessage = kickMessage;
        this.actionName = actionName;
        this.triggeredMods = triggeredMods;
        this.isRequired = isRequired;
        this.isBlacklisted = isBlacklisted;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public String getActionName() {
        return actionName;
    }

    public Set<String> getTriggeredMods() {
        return triggeredMods;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public boolean isBlacklisted() {
        return isBlacklisted;
    }

    public boolean shouldKick() {
        return kickMessage != null;
    }
}
