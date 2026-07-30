package org.example.service.auth;

public interface UserSessionRepository {
    boolean isUserLoggedIn(long userId);
    void setUserLoggedInStatus(long userId, boolean isLoggedIn);
}