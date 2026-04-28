package me.mklv.handshaker.common.commands;

import java.util.Map;
import java.util.Locale;

import me.mklv.handshaker.common.configs.ConfigTypes.ConfigState;

public final class CommandVisualOperations {
    private CommandVisualOperations() {
    }

    public static final class Colors {
        private static final int DEFAULT_COLOR = 0xB19859;

        private static final Map<String, Integer> COLORS = Map.of(
            "dirtyGold", 0xB19859,
            "successGreen", 0x4CAF50,
            "warningAmber", 0xFFB300,
            "errorRed", 0xD32F2F,
            "infoBlue", 0x1976D2,

            "gray",0xAAAAAA,
            "darkGray", 0x555555,
            "yellow", 0xFFFF55,
            "white", 0xFFFFFF,
            "aqua", 0x55FFFF

        );

        public static final int DIRTY_GOLD_COLOR = COLORS.get("dirtyGold");
        public static final int SUCCESS_GREEN_COLOR = COLORS.get("successGreen");
        public static final int WARNING_AMBER_COLOR = COLORS.get("warningAmber");
        public static final int ERROR_RED_COLOR = COLORS.get("errorRed");
        public static final int INFO_BLUE_COLOR = COLORS.get("infoBlue");
        public static final int GRAY = COLORS.get("gray");
        public static final int DARK_GRAY = COLORS.get("darkGray");
        public static final int YELLOW = COLORS.get("yellow");
        public static final int WHITE = COLORS.get("white");
        public static final int AQUA = COLORS.get("aqua");

        private Colors() {
        }

        public static int color(String key) {
            return COLORS.getOrDefault(key, DEFAULT_COLOR);
        }
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
