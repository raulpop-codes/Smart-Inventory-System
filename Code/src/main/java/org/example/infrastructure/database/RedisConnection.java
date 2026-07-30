package org.example.infrastructure.database;

import redis.clients.jedis.JedisPool;

public class RedisConnection {

    private static final String HOST = requireEnv("REDIS_HOST");
    private static final int PORT = Integer.parseInt(requireEnv("REDIS_PORT"));

    private static final JedisPool jedisPool = new JedisPool(HOST, PORT);

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Environment variable " + name + " is not set!");
        }
        return value;
    }

    public static JedisPool getPool() {
        return jedisPool;
    }
}