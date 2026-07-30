package org.example.service.auth;

import org.example.model.auth.User;

import java.util.Optional;

public interface UserRepository {
    void registerUser(User user) throws Exception;
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<Long> findUserIdByUsername(String username);
}
