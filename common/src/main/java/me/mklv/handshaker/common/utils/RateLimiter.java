package me.mklv.handshaker.common.utils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimiter {
    private final int maxTokens;
    private final long refillIntervalMillis;
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxPerWindow, long windowSeconds) {
        this.maxTokens = Math.max(1, maxPerWindow);
        this.refillIntervalMillis = Math.max(1_000L, windowSeconds * 1_000L);
    }

    public boolean tryConsume(UUID key) {
        if (key == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(maxTokens, now));
        synchronized (bucket) {
            refill(bucket, now);
            if (bucket.tokens < 1) {
                return false;
            }
            bucket.tokens--;
            bucket.lastSeen = now;
            return true;
        }
    }

    public void cleanupIdle(long idleMillis) {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > idleMillis);
    }

    private void refill(Bucket bucket, long now) {
        long elapsed = now - bucket.lastRefill;
        if (elapsed <= 0) {
            return;
        }
        if (elapsed >= refillIntervalMillis) {
            long windows = elapsed / refillIntervalMillis;
            long refill = windows * maxTokens;
            bucket.tokens = (int) Math.min(maxTokens, bucket.tokens + refill);
            bucket.lastRefill += windows * refillIntervalMillis;
        }
    }

    private static final class Bucket {
        private int tokens;
        private long lastRefill;
        private long lastSeen;

        private Bucket(int tokens, long now) {
            this.tokens = tokens;
            this.lastRefill = now;
            this.lastSeen = now;
        }
    }
}
