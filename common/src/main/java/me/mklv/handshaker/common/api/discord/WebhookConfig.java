package me.mklv.handshaker.common.api.discord;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record WebhookConfig(
    boolean enabled,
    String discordUrl,
    String slackUrl,
    Set<WebhookEventType> events
) {
    public WebhookConfig {
        discordUrl = normalize(discordUrl);
        slackUrl = normalize(slackUrl);
        events = events == null || events.isEmpty()
            ? EnumSet.noneOf(WebhookEventType.class)
            : Collections.unmodifiableSet(EnumSet.copyOf(events));
    }

    public static WebhookConfig disabled() {
        return new WebhookConfig(false, "", "", EnumSet.noneOf(WebhookEventType.class));
    }

    public boolean shouldSend(WebhookEventType eventType) {
        return enabled && eventType != null && events.contains(eventType) && hasAnyTarget();
    }

    public boolean hasAnyTarget() {
        return !discordUrl.isBlank() || !slackUrl.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
