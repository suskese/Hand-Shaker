package me.mklv.handshaker.fabric.server.utils;

public class CachedValue<T> {
    private T value;
    private long expiresAt;
    private final long ttlMs;

    /**
     * Creates a new cached value with the specified TTL.
     * @param ttlMs Time-to-live in milliseconds
     */
    public CachedValue(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    /**
     * Gets the cached value if it hasn't expired.
     * @return The cached value, or null if expired/not set
     */
    public synchronized T get() {
        if (value != null && System.currentTimeMillis() < expiresAt) {
            return value;
        }
        return null;
    }

    /**
     * Sets a new value and resets the expiration time.
     * @param newValue The value to cache
     */
    public synchronized void set(T newValue) {
        this.value = newValue;
        this.expiresAt = System.currentTimeMillis() + ttlMs;
    }

    /**
     * Invalidates the cached value.
     */
    public synchronized void invalidate() {
        this.value = null;
        this.expiresAt = 0;
    }
}