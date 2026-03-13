package me.mklv.handshaker.common.api.discord;

import java.time.Instant;

public record WebhookPayload(
    WebhookEventType event,
    String player,
    String mod,
    String reason,
    Instant timestamp
) {
    public WebhookPayload {
        timestamp = timestamp == null ? Instant.now() : timestamp;
    }
}
