package org.example.infrastructure.auth;

import org.example.infrastructure.database.RedisConnection;
import org.example.service.auth.RateLimiterRepository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisRateLimiterRepository implements RateLimiterRepository {
    private final JedisPool jedisPool;

    public RedisRateLimiterRepository() {
        this(RedisConnection.getPool());
    }

    public RedisRateLimiterRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public boolean isAllowed(String key, int maxRequests, int windowSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = "rate_limit:" + key;

            // Atomically increment request counter
            long currentRequests = jedis.incr(redisKey);

            // Set expiration window on the first request
            if (currentRequests == 1) {
                jedis.expire(redisKey, windowSeconds);
            }

            return currentRequests <= maxRequests;
        }
    }

    @Override
    public long getRemainingTTL(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String redisKey = "rate_limit:" + key;
            long ttl = jedis.ttl(redisKey);
            return Math.max(ttl, 0L);
        }
    }

}