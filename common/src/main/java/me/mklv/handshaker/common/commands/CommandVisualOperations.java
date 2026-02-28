package me.mklv.handshaker.common.commands;

import java.util.Locale;

import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;

public final class CommandVisualOperations {
    private CommandVisualOperations() {
    }

    public static String sectionHeaderText(String title) {
        return "========== " + title + " ==========";
    }

    public static String sectionFooterText(String title) {
        return "=".repeat(Math.max(1, sectionHeaderText(title).length() - 2));
    }

    public static String actionDisplayLabel(ConfigState.ModConfig modCfg) {
        if (modCfg == null) {
            return "none";
        }
        String actionName = modCfg.getActionName();
        if (actionName == null || actionName.isBlank() || "none".equalsIgnoreCase(actionName)) {
            return "none";
        }
        return actionName.toLowerCase(Locale.ROOT);
    }

    public static String changeHoverText(ConfigState.ModConfig modCfg) {
        String modeLabel = modCfg != null && modCfg.getMode() != null ? modCfg.getMode() : "allowed";
        return modeLabel + " " + actionDisplayLabel(modCfg) + "\n\nClick to prepare change command";
    }

    public static String removeHoverText() {
        return "Click to remove";
    }

    public static String showPlayersUsingModHoverText() {
        return "Click to show players using this mod";
    }

    public static String noModsConfiguredText() {
        return "No mods configured";
    }

    public static String noModsDetectedText() {
        return "No mods detected";
    }

    public static String pageLabelPrefix() {
        return "page: ";
    }

    public static String actionLabelPrefix() {
        return " | Action: ";
    }

    public static String navigateUsageText(String fallbackUsage) {
        return "Use " + fallbackUsage + " to navigate";
    }

    public static String previousPageLabel() {
        return "[ < Previous page ]";
    }

    public static String nextPageLabel() {
        return "[ Next page > ]";
    }

    public static String escapeQuotes(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    public static String quoted(String value) {
        return "\"" + escapeQuotes(value) + "\"";
    }

    public static String manageChangeCommand(String commandRoot, String modName) {
        return commandRoot + " manage change " + quoted(modName);
    }

    public static String manageRemoveCommand(String commandRoot, String modName) {
        return commandRoot + " manage remove " + quoted(modName);
    }

    public static String infoModCommand(String commandRoot, String modToken) {
        return commandRoot + " info mod " + quoted(modToken);
    }
}
