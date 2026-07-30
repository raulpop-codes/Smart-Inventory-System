package org.example.service.auth;

import org.example.model.auth.User;
import org.example.util.AppLogger;
import org.example.util.DeviceUtil;
import org.example.util.LogLevel;

import java.util.Optional;

public class AuthService {
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final DeviceLockoutRepository lockoutRepository;
    private final RateLimiterRepository rateLimiterRepository;

    private static final int GLOBAL_MAX_LOGIN_PER_SEC = 50; // Maximum 50 global login attempts per second
    private static final int GLOBAL_MAX_REGISTER_PER_SEC = 10; // Maximum 10 global registrations per second

    public AuthService(UserRepository userRepository,
                       UserSessionRepository sessionRepository,
                       DeviceLockoutRepository lockoutRepository,
                       RateLimiterRepository rateLimiterRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.lockoutRepository = lockoutRepository;
        this.rateLimiterRepository = rateLimiterRepository;
    }

    /**
     * Registers a new user account after validating global/device rate limits and checking existing credentials.
     */
    public void register(String username, String password, String confirmPassword, String email) throws Exception {
        String deviceId = DeviceUtil.getDeviceId();

        if (!rateLimiterRepository.isAllowed("global:register", GLOBAL_MAX_REGISTER_PER_SEC, 1)) {
            AppLogger.log(LogLevel.WARN, String.format("[%s] RateLimit_Global: Global register rate limit exceeded.", deviceId));
            throw new Exception("The server is currently busy. Please try again in a few seconds.");
        }

        String deviceRegisterKey = "register:" + deviceId;
        if (!rateLimiterRepository.isAllowed(deviceRegisterKey, 3, 300)) {
            AppLogger.log(LogLevel.WARN, String.format("[%s] RateLimit_Device: Device register rate limit exceeded.", deviceId));

            long remaining = rateLimiterRepository.getRemainingTTL(deviceRegisterKey);
            long min = remaining / 60;
            long sec = remaining % 60;

            throw new Exception(String.format("Too many registration attempts! Please wait %d min and %d sec.", min, sec));
        }

        if (!password.equals(confirmPassword)) {
            throw new Exception("Passwords do not match!");
        }

        if (userRepository.findByUsername(username).isPresent()) {
            throw new Exception("Username is already taken.");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new Exception("Email address is already in use.");
        }

        User newUser = new User(username, password, email);
        userRepository.registerUser(newUser);

        AppLogger.log(LogLevel.AUTH, String.format("[%s] Register_Success: New user registered successfully: %s (Email: %s)",
                deviceId, username, email));
    }

    /**
     * Authenticates a user by username or email, verifying rate limits, device lockouts, and credentials.
     */
    public Optional<Long> login(String usernameOrEmail, String password) throws Exception {
        String deviceId = DeviceUtil.getDeviceId();

        if (!rateLimiterRepository.isAllowed("global:login", GLOBAL_MAX_LOGIN_PER_SEC, 1)) {
            AppLogger.log(LogLevel.WARN, String.format("[%s] RateLimit_Global: Global login rate limit exceeded.", deviceId));
            throw new Exception("The server is experiencing high traffic. Please try again in 2-3 seconds.");
        }

        if (!rateLimiterRepository.isAllowed("login:" + deviceId, 10, 60)) {
            AppLogger.log(LogLevel.WARN, String.format("[%s] RateLimit_Device: Device login rate limit exceeded.", deviceId));
            throw new Exception("Too many requests from this device! Please wait a minute.");
        }

        if (lockoutRepository.isDeviceBlocked(deviceId)) {
            long remaining = lockoutRepository.getRemainingBlockTimeSeconds(deviceId);
            long min = remaining / 60;
            long sec = remaining % 60;
            AppLogger.log(LogLevel.WARN, String.format("[%s] Device_Locked: Blocked device attempted login.", deviceId));
            throw new Exception(String.format("Device blocked! Please try again in %d min and %d sec.", min, sec));
        }

        Optional<User> userOpt = usernameOrEmail.contains("@")
                ? userRepository.findByEmail(usernameOrEmail)
                : userRepository.findByUsername(usernameOrEmail);

        if (userOpt.isEmpty() || !userOpt.get().checkUser(userOpt.get().getUsername(), password)) {
            int attempts = lockoutRepository.recordFailedAttempt(deviceId);
            AppLogger.log(LogLevel.WARN, String.format("[%s] Auth_Failed: Failed login attempt for identifier: %s | Total failed attempts: %d",
                    deviceId, usernameOrEmail, attempts));

            if (attempts >= 3) {
                AppLogger.log(LogLevel.WARN, String.format("[%s] Device_Lockout: Device locked out due to multiple failed logins.", deviceId));
                throw new Exception("Maximum login attempts exceeded! The device has been blocked for 5 minutes.");
            }
            throw new Exception("Invalid credentials! Remaining attempts: " + (3 - attempts));
        }

        String actualUsername = userOpt.get().getUsername();
        Long userId = userRepository.findUserIdByUsername(actualUsername)
                .orElseThrow(() -> {
                    AppLogger.log(LogLevel.ERROR, String.format("[%s] Internal_Error: User ID not found for username: %s", deviceId, actualUsername));
                    return new Exception("Internal error: User ID not found.");
                });

        if (sessionRepository.isUserLoggedIn(userId)) {
            AppLogger.log(LogLevel.WARN, String.format("[%s] Concurrent_Login: User ID %d attempted concurrent login while already active.", deviceId, userId));
            throw new Exception("User is already logged in on another session!");
        }

        // 7. Success Cleanup
        lockoutRepository.resetFailedAttempts(deviceId);
        sessionRepository.setUserLoggedInStatus(userId, true);

        AppLogger.log(LogLevel.AUTH, String.format("[%s] Login_Success: User successfully logged in: %s (ID: %d)",
                deviceId, actualUsername, userId));

        return Optional.of(userId);
    }

    /**
     * Terminates a user session and updates logged-in status.
     */
    public void logout(long userId) {
        String deviceId = DeviceUtil.getDeviceId();
        sessionRepository.setUserLoggedInStatus(userId, false);
        AppLogger.log(LogLevel.AUTH, String.format("[%s] Logout_Success: User logged out (ID: %d)", deviceId, userId));
    }
}