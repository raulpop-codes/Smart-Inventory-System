package org.example.infrastructure.auth;

import org.example.infrastructure.database.RedisConnection;
import org.example.service.auth.DeviceLockoutRepository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisDeviceLockoutRepository implements DeviceLockoutRepository {
    private final JedisPool jedisPool;

    private static final int BLOCK_TIME_SECONDS = 300;
    private static final int MAX_ALLOWED_ATTEMPTS = 3;

    public RedisDeviceLockoutRepository() {
        this(RedisConnection.getPool());
    }

    public RedisDeviceLockoutRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public boolean isDeviceBlocked(String deviceId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String blockKey = "blocked_device:" + deviceId;
            // If the key exists, the device is still within the 5-minute block window
            return jedis.exists(blockKey);
        }
    }

    @Override
    public long getRemainingBlockTimeSeconds(String deviceId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String blockKey = "blocked_device:" + deviceId;
            long ttl = jedis.ttl(blockKey); // Returns remaining time in seconds

            // ttl returns -2 if key doesn't exist, -1 if no expire time.
            // return 0 if unblocked
            return Math.max(ttl, 0L);
        }
    }

    @Override
    public int recordFailedAttempt(String deviceId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String attemptsKey = "failed_attempts:" + deviceId;

            // Atomically increment the counter in Redis and get the new attempt count
            long attempts = jedis.incr(attemptsKey);

            if (attempts >= MAX_ALLOWED_ATTEMPTS) {
                String blockKey = "blocked_device:" + deviceId;

                // Save the block flag with automatic expiration after 300 seconds (5 minutes)
                jedis.setex(blockKey, BLOCK_TIME_SECONDS, "blocked");

                // Reset the attempt counter so it's clean when the block expires
                jedis.del(attemptsKey);
            }

            return (int) attempts;
        }
    }

    @Override
    public void resetFailedAttempts(String deviceId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String attemptsKey = "failed_attempts:" + deviceId;
            String blockKey = "blocked_device:" + deviceId;

            // Clear both keys once the user successfully authenticates
            jedis.del(attemptsKey, blockKey);
        }
    }
}