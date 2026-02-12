package me.mklv.handshaker.common.configs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ModCheckResult {
    private final String message;
    private final String actionName;
    private final Set<String> mods;
    private final boolean requiredViolation;
    private final boolean blacklistedViolation;
    private final boolean whitelistViolation;
    private final Map<String, String> allowedActionsByMod;

    private ModCheckResult(String message,
                           String actionName,
                           Set<String> mods,
                           boolean requiredViolation,
                           boolean blacklistedViolation,
                           boolean whitelistViolation,
                           Map<String, String> allowedActionsByMod) {
        this.message = message;
        this.actionName = actionName;
        this.mods = mods != null ? Collections.unmodifiableSet(mods) : Collections.emptySet();
        this.requiredViolation = requiredViolation;
        this.blacklistedViolation = blacklistedViolation;
        this.whitelistViolation = whitelistViolation;
        this.allowedActionsByMod = allowedActionsByMod != null
            ? Collections.unmodifiableMap(allowedActionsByMod)
            : Collections.emptyMap();
    }

    public static ModCheckResult violation(String message,
                                           String actionName,
                                           Set<String> mods,
                                           boolean requiredViolation,
                                           boolean blacklistedViolation,
                                           boolean whitelistViolation) {
        return new ModCheckResult(
            message,
            actionName,
            mods != null ? new LinkedHashSet<>(mods) : new LinkedHashSet<>(),
            requiredViolation,
            blacklistedViolation,
            whitelistViolation,
            new LinkedHashMap<>()
        );
    }

    public static ModCheckResult allowedActions(Map<String, String> allowedActionsByMod) {
        String firstAction = null;
        Set<String> mods = new LinkedHashSet<>();
        if (allowedActionsByMod != null && !allowedActionsByMod.isEmpty()) {
            for (Map.Entry<String, String> entry : allowedActionsByMod.entrySet()) {
                mods.add(entry.getKey());
                if (firstAction == null) {
                    firstAction = entry.getValue();
                }
            }
        }
        return new ModCheckResult(null, firstAction, mods, false, false, false,
            allowedActionsByMod != null ? new LinkedHashMap<>(allowedActionsByMod) : new LinkedHashMap<>());
    }

    public static ModCheckResult empty() {
        return new ModCheckResult(null, null, new LinkedHashSet<>(), false, false, false, new LinkedHashMap<>());
    }

    public String getMessage() {
        return message;
    }

    public String getActionName() {
        return actionName;
    }

    public Set<String> getMods() {
        return mods;
    }

    public boolean isRequiredViolation() {
        return requiredViolation;
    }

    public boolean isBlacklistedViolation() {
        return blacklistedViolation;
    }

    public boolean isWhitelistViolation() {
        return whitelistViolation;
    }

    public Map<String, String> getAllowedActionsByMod() {
        return allowedActionsByMod;
    }

    public boolean isViolation() {
        return message != null && !message.isEmpty();
    }

    public boolean hasAllowedActions() {
        return !allowedActionsByMod.isEmpty();
    }
}
