package com.expensetracker.service;

import com.expensetracker.entity.User;
import java.util.List;

public interface UserService {

    User registerUser(User user);

    List<User> getAllUsers();

    User getUserById(Long id);

    void deleteUser(Long id);

    long getUserCount();

    User loginUser(String email, String password);

    boolean emailExists(String email);

    User updateProfile(
            Long userId,
            String username,
            String currentPassword,
            String newPassword
    );

    User updateUsername(Long userId, String username);

    String generateApiKey(Long userId);

    String getApiKey(Long userId);
}