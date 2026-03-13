package me.mklv.handshaker.common.api.discord;

import com.google.gson.Gson;
import me.mklv.handshaker.common.utils.Gsons;
import org.slf4j.Logger;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebhookDispatcher {
    private static final Pattern MOD_TOKEN_PATTERN = Pattern.compile("\\b([a-z0-9_.-]+:[a-z0-9_.+-]+)\\b", Pattern.CASE_INSENSITIVE);
    private final Gson gson = Gsons.create();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Logger logger;
    private volatile WebhookConfig config;

    public WebhookDispatcher(WebhookConfig config, Logger logger) {
        this.config = config != null ? config : WebhookConfig.disabled();
        this.logger = logger;
    }

    public void updateConfig(WebhookConfig config) {
        this.config = config != null ? config : WebhookConfig.disabled();
    }

    public void publish(WebhookEventType eventType, String player, String mod, String reason) {
        WebhookConfig current = this.config;
        if (!current.shouldSend(eventType)) {
            return;
        }

        WebhookPayload payload = new WebhookPayload(eventType, player, mod, reason, Instant.now());
        String rawJson = gson.toJson(payload);

        if (!current.discordUrl().isBlank()) {
            executor.submit(() -> postJson(current.discordUrl(), toDiscordBody(payload, rawJson)));
        }
        if (!current.slackUrl().isBlank()) {
            executor.submit(() -> postJson(current.slackUrl(), toSlackBody(payload, rawJson)));
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private String toDiscordBody(WebhookPayload payload, String rawJson) {
        Map<String, Object> body = new LinkedHashMap<>();

        Map<String, Object> embed = new LinkedHashMap<>();
        String modValue = resolveMod(payload.mod(), payload.reason());
        embed.put("title", buildDiscordTitle(payload));
        embed.put("description", payload.reason() != null && !payload.reason().isBlank() ? payload.reason() : rawJson);
        embed.put("timestamp", payload.timestamp().toString());

        Map<String, Object> playerField = new LinkedHashMap<>();
        playerField.put("name", "Player");
        playerField.put("value", safe(payload.player()));
        playerField.put("inline", true);

        Map<String, Object> modField = new LinkedHashMap<>();
        modField.put("name", "Mod");
        modField.put("value", modValue);
        modField.put("inline", true);

        embed.put("fields", new Object[]{playerField, modField});

        body.put("embeds", new Object[]{embed});
        return gson.toJson(body);
    }

    private String buildDiscordTitle(WebhookPayload payload) {
        String player = safe(payload.player());
        if (player.isBlank()) {
            return "HandShaker " + payload.event();
        }

        return switch (payload.event()) {
            case PLAYER_KICKED -> player + " was kicked";
            case PLAYER_BANNED -> player + " was banned";
            default -> "HandShaker " + payload.event();
        };
    }

    private String resolveMod(String mod, String reason) {
        String direct = safe(mod).trim();
        if (!direct.isBlank()) {
            return direct;
        }

        String parsed = parseModFromReason(reason);
        return parsed == null ? "" : parsed;
    }

    private String parseModFromReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }

        Matcher matcher = MOD_TOKEN_PATTERN.matcher(reason.toLowerCase(Locale.ROOT));
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String toSlackBody(WebhookPayload payload, String rawJson) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", "HandShaker event: " + payload.event()
            + " | player=" + safe(payload.player())
            + " | mod=" + safe(payload.mod())
            + " | reason=" + safe(payload.reason()));
        body.put("raw", rawJson);
        return gson.toJson(body);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void postJson(String urlValue, String body) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlValue).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
            }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                warn("Webhook returned status " + code + " for " + urlValue);
            }
        } catch (Exception e) {
            warn("Webhook post failed for " + urlValue + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warn(message);
        }
    }
}
