package me.mklv.handshaker.common.configs;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessagePlaceholderExpander {
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\\{messages\\.([^}]+)\\}");
    private static final int MOD_TEXT_LIMIT = 220;

    private MessagePlaceholderExpander() {
    }

    public static String expand(String command,
                                String playerName,
                                String modText,
                                Map<String, String> messages) {
        if (command == null) {
            return null;
        }

        String result = replaceMessagePlaceholders(command, messages);
        String safePlayer = playerName != null ? playerName : "";
        String safeMod = truncateModText(modText);

        return result
            .replace("{player}", safePlayer)
            .replace("{mod}", safeMod)
            .replace("{mods}", safeMod);
    }

    private static String truncateModText(String modText) {
        if (modText == null) {
            return "";
        }

        String normalized = modText.trim();
        if (normalized.length() <= MOD_TEXT_LIMIT) {
            return normalized;
        }

        int splitAt = normalized.lastIndexOf(", ", MOD_TEXT_LIMIT);
        if (splitAt < 0) {
            splitAt = MOD_TEXT_LIMIT;
        }

        String shown = normalized.substring(0, splitAt);
        int remaining = 1;
        for (int i = splitAt; i < normalized.length() - 1; i++) {
            if (normalized.charAt(i) == ',' && normalized.charAt(i + 1) == ' ') {
                remaining++;
            }
        }
        return shown + ", and " + remaining + " more";
    }

    private static String replaceMessagePlaceholders(String command, Map<String, String> messages) {
        if (messages == null || messages.isEmpty()) {
            return command;
        }

        Matcher matcher = MESSAGE_PATTERN.matcher(command);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String messageKey = matcher.group(1);
            String messageValue = messages.getOrDefault(messageKey, "{messages." + messageKey + "}");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(messageValue));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
