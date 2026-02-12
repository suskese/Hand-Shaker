package me.mklv.handshaker.common.configs;

public final class ConfigLoadOptions {
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
