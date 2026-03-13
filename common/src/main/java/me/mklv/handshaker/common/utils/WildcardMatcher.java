package me.mklv.handshaker.common.utils;

public final class WildcardMatcher {
    private WildcardMatcher() {
    }

    public static boolean containsWildcard(String value) {
        return value != null && (value.indexOf('*') >= 0 || value.indexOf('?') >= 0);
    }

    public static boolean matches(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }

        int patternIndex = 0;
        int valueIndex = 0;
        int starIndex = -1;
        int matchIndex = 0;

        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length()
                && (pattern.charAt(patternIndex) == '?' || pattern.charAt(patternIndex) == value.charAt(valueIndex))) {
                patternIndex++;
                valueIndex++;
                continue;
            }

            if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                matchIndex = valueIndex;
                continue;
            }

            if (starIndex != -1) {
                patternIndex = starIndex + 1;
                valueIndex = ++matchIndex;
                continue;
            }

            return false;
        }

        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }

        return patternIndex == pattern.length();
    }
}