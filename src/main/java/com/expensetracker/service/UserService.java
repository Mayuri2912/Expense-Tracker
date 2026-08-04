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

    /**
     * Updates the logged-in user's full name and, optionally, their password.
     * @param currentPassword required to confirm identity before any change is made
     * @param newPassword     leave blank/null to keep the existing password
     */
    User updateProfile(Long userId, String fullName, String currentPassword, String newPassword);

    /**
     * Updates only the user's full name - no password confirmation required.
     * Used by the Profile page, which no longer handles password changes
     * (see the dedicated Security page / {@link #updateProfile} for that).
     */
    User updateFullName(Long userId, String fullName);

}
