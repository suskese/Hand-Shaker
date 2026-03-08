package me.mklv.handshaker.common.protocols;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ApprovedHashes {
    public interface LogSink {
        void info(String message);
        void warn(String message);
    }

    private static final String EMBEDDED_RESOURCE = "/configs/hash-list.yml";
    private static final Pattern SHA256_HEX = Pattern.compile("(?i)\\b[a-f0-9]{64}\\b");
    private static final int CONNECT_TIMEOUT_MS = 3500;
    private static final int READ_TIMEOUT_MS = 3500;

    private static final List<String> REMOTE_SOURCES = List.of(
        "https://github.com/suskese/Hand-Shaker/blob/dev/common/src/main/resources/configs/hash-list.yml",
        "https://github.com/suskese/Hand-Shaker/blob/main/common/src/main/resources/configs/hash-list.yml"
    );

    private static final AtomicReference<Set<String>> TRUSTED_HASHES = new AtomicReference<>(Set.of());
    private static volatile Instant lastRefresh = Instant.EPOCH;

    private ApprovedHashes() {
    }

    public static void initialize(Class<?> anchor, LogSink logger, boolean debug) {
        Set<String> loaded = new LinkedHashSet<>(loadEmbedded(anchor, logger));
        int embeddedCount = loaded.size();

        int remoteAdded = 0;
        for (String source : REMOTE_SOURCES) {
            try {
                String rawUrl = toRawGithubUrl(source);
                String payload = fetchText(rawUrl);
                Set<String> remoteHashes = extractHashes(payload);
                int before = loaded.size();
                loaded.addAll(remoteHashes);
                remoteAdded += Math.max(0, loaded.size() - before);
                if (debug) {
                    logger.info("Hybrid hash source loaded: " + rawUrl + " (" + remoteHashes.size() + " hashes)");
                }
            } catch (Exception ex) {
                logger.warn("Failed to refresh approved hybrid hashes from " + source + ": " + ex.getMessage());
            }
        }

        Set<String> finalSet = Set.copyOf(loaded);
        TRUSTED_HASHES.set(finalSet);
        lastRefresh = Instant.now();

        logger.info("Hybrid hash allowlist initialized: embedded=" + embeddedCount + ", remote_added=" + remoteAdded + ", total=" + finalSet.size());
    }

    public static boolean isTrusted(String jarHash) {
        if (jarHash == null || jarHash.isBlank()) {
            return false;
        }
        return TRUSTED_HASHES.get().contains(jarHash.trim().toLowerCase(Locale.ROOT));
    }

    public static int size() {
        return TRUSTED_HASHES.get().size();
    }

    public static Instant getLastRefresh() {
        return lastRefresh;
    }

    private static Set<String> loadEmbedded(Class<?> anchor, LogSink logger) {
        if (anchor == null) {
            return Set.of();
        }

        try (InputStream stream = anchor.getResourceAsStream(EMBEDDED_RESOURCE)) {
            if (stream == null) {
                logger.warn("Embedded approved hash resource not found: " + EMBEDDED_RESOURCE);
                return Set.of();
            }

            String text = readAll(stream);
            return extractHashes(text);
        } catch (Exception ex) {
            logger.warn("Failed to load embedded approved hashes: " + ex.getMessage());
            return Set.of();
        }
    }

    private static String toRawGithubUrl(String source) {
        if (source == null) {
            return "";
        }

        String trimmed = source.trim();
        String marker = "github.com/suskese/Hand-Shaker/blob/";
        int markerIndex = trimmed.indexOf(marker);
        if (markerIndex < 0) {
            return trimmed;
        }

        String tail = trimmed.substring(markerIndex + marker.length());
        int slash = tail.indexOf('/');
        if (slash <= 0) {
            return trimmed;
        }

        String branch = tail.substring(0, slash);
        String path = tail.substring(slash + 1);
        return "https://raw.githubusercontent.com/suskese/Hand-Shaker/" + branch + "/" + path;
    }

    private static String fetchText(String urlText) throws Exception {
        URL url = URI.create(urlText).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "HandShaker/7");

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code);
        }

        try (InputStream stream = connection.getInputStream()) {
            return readAll(stream);
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static Set<String> extractHashes(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }

        LinkedHashSet<String> hashes = new LinkedHashSet<>();
        Matcher matcher = SHA256_HEX.matcher(text);
        while (matcher.find()) {
            hashes.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return hashes;
    }
}