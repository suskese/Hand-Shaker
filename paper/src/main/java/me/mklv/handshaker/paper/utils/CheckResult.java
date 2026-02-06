package me.mklv.handshaker.paper.utils;

import java.util.Set;

public record CheckResult(
    String kickMessage,
    String actionName,
    Set<String> detectedMods,
    boolean isMissingRequired,
    boolean isBlacklisted
) {
    public CheckResult(String kickMessage, String actionName, Set<String> detectedMods, boolean isMissingRequired, boolean isBlacklisted) {
        this.kickMessage = kickMessage;
        this.actionName = actionName;
        this.detectedMods = detectedMods;
        this.isMissingRequired = isMissingRequired;
        this.isBlacklisted = isBlacklisted;
    }

    public boolean hasViolation() {
        return kickMessage != null && !kickMessage.isEmpty();
    }

    public String getMessage() {
        return kickMessage;
    }

    public String getAction() {
        return actionName;
    }

    public Set<String> getMods() {
        return detectedMods;
    }

    public boolean isMissingRequired() {
        return isMissingRequired;
    }

    public boolean isBlacklistedMod() {
        return isBlacklisted;
    }
}
