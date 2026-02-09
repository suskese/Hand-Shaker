package me.mklv.handshaker.common.utils;

public class StringUtils {

    public static String truncate(String str, int maxLength) {
        if (str == null) return "";
        return str.substring(0, Math.min(maxLength, str.length()));
    }

    public static String safePlayerName(String playerName) {
        return playerName == null || playerName.isEmpty() ? "Unknown" : playerName;
    }

    public static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }
}