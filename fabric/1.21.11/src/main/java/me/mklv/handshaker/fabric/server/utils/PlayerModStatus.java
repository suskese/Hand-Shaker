package me.mklv.handshaker.fabric.server.utils;

import java.util.Set;

public class PlayerModStatus {
    private final String kickMessage;
    private final String actionName;
    private final Set<String> detectedMods;
    private final boolean isRequiredViolation;
    private final boolean isBlacklistedViolation;

    public PlayerModStatus(String kickMessage, String actionName, Set<String> detectedMods, boolean isRequiredViolation, boolean isBlacklistedViolation) {
        this.kickMessage = kickMessage;
        this.actionName = actionName;
        this.detectedMods = detectedMods;
        this.isRequiredViolation = isRequiredViolation;
        this.isBlacklistedViolation = isBlacklistedViolation;
    }

    public String getKickMessage() {
        return kickMessage;
    }

    public String getActionName() {
        return actionName;
    }

    public Set<String> getDetectedMods() {
        return detectedMods;
    }

    public boolean isRequiredViolation() {
        return isRequiredViolation;
    }

    public boolean isBlacklistedViolation() {
        return isBlacklistedViolation;
    }

    public boolean hasViolation() {
        return kickMessage != null;
    }
}
