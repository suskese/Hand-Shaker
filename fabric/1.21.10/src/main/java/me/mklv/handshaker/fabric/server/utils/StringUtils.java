package me.mklv.handshaker.fabric.server.utils;

/**
 * Utility methods for string operations.
 */
public class StringUtils {
    /**
     * Truncates a string to a maximum length, useful for displaying hashes and IDs.
     * @param str The string to truncate
     * @param maxLength The maximum length to display
     * @return The truncated string, or the original if shorter than maxLength
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.substring(0, Math.min(maxLength, str.length()));
    }

    /**
     * Safely gets player name with fallback if null.
     * @param playerName The player name to validate
     * @return The player name or "Unknown" if null/empty
     */
    public static String safePlayerName(String playerName) {
        return playerName == null || playerName.isEmpty() ? "Unknown" : playerName;
    }

    /**
     * Checks if a string is null or empty.
     * @param str The string to check
     * @return True if the string is null or empty, false otherwise
     */
    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}
