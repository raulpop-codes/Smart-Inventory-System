package org.example.infrastructure.auth;

import org.example.infrastructure.database.RedisConnection;
import org.example.service.auth.UserSessionRepository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisUserSessionRepository implements UserSessionRepository {

    private final JedisPool jedisPool;

    public RedisUserSessionRepository() {
        this(RedisConnection.getPool());
    }

    public RedisUserSessionRepository(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    public boolean isUserLoggedIn(long userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String sessionKey = "user_session:" + userId;
            return jedis.exists(sessionKey);
        }
    }

    @Override
    public void setUserLoggedInStatus(long userId, boolean isLoggedIn) {
        try (Jedis jedis = jedisPool.getResource()) {
            String sessionKey = "user_session:" + userId;

            if (isLoggedIn) {
                // Save the session indefinitely (without an expiration time / TTL)
                jedis.set(sessionKey, "active");
            } else {
                // Delete the key from Redis on logout
                jedis.del(sessionKey);
            }
        }
    }
}