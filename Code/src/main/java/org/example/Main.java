package org.example;

import org.example.infrastructure.auth.MySQLAuthRepository;
import org.example.infrastructure.auth.RedisDeviceLockoutRepository;
import org.example.infrastructure.auth.RedisRateLimiterRepository;
import org.example.infrastructure.auth.RedisUserSessionRepository;
import org.example.infrastructure.database.RedisConnection;
import org.example.service.auth.*;
import org.example.ui.LoginJFrame;
import redis.clients.jedis.JedisPool;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        JedisPool redisPool = RedisConnection.getPool();

        UserRepository userRepository = new MySQLAuthRepository();
        UserSessionRepository sessionRepository = new RedisUserSessionRepository(redisPool);
        DeviceLockoutRepository lockoutRepository = new RedisDeviceLockoutRepository(redisPool);
        RateLimiterRepository rateLimiterRepository = new RedisRateLimiterRepository(redisPool);

        AuthService authService = new AuthService(userRepository, sessionRepository, lockoutRepository, rateLimiterRepository);

        SwingUtilities.invokeLater(() -> {
            new LoginJFrame(authService);
        });
    }
}