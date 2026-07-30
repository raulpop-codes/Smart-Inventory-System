package org.example.service.auth;

public interface RateLimiterRepository {
    /**
     * Checks if an action is allowed for a given key within a time window.
     *
     * @param key Identifies the bucket (e.g., "rate:login:device_123")
     * @param maxRequests Maximum allowed actions in the window
     * @param windowSeconds Window size in seconds
     * @return true if allowed, false if limit exceeded
     */
    boolean isAllowed(String key, int maxRequests, int windowSeconds);

    /**
     * Retrieves the remaining Time-To-Live (TTL) in seconds for a rate limit key.
     *
     * @param key Identifies the bucket (e.g., "register:device_123")
     * @return Remaining time in seconds before the limit resets, or 0 if expired or not found
     */
    long getRemainingTTL(String key);
}