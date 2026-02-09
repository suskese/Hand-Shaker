package me.mklv.handshaker.common.utils;

public class CachedValue<T> {
    private T value;
    private long expiresAt;
    private final long ttlMs;

    public CachedValue(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public synchronized T get() {
        if (value != null && System.currentTimeMillis() < expiresAt) {
            return value;
        }
        return null;
    }

    public synchronized void set(T newValue) {
        this.value = newValue;
        this.expiresAt = System.currentTimeMillis() + ttlMs;
    }

    public synchronized void invalidate() {
        this.value = null;
        this.expiresAt = 0;
    }
}
