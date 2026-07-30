package org.example.service.auth;

public interface DeviceLockoutRepository {
    boolean isDeviceBlocked(String deviceId);
    long getRemainingBlockTimeSeconds(String deviceId);
    int recordFailedAttempt(String deviceId);
    void resetFailedAttempts(String deviceId);
}