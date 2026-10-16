package com.expensetracker.service;

import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User registerUser(User user) {

        if (user.getUsername() != null) {
            user.setUsername(user.getUsername().trim());
        }
        if (user.getEmail() != null) {
            user.setEmail(user.getEmail().trim());
        }

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new IllegalArgumentException("Name is required");
        }

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("This username is already in use");
        }

        if (emailExists(user.getEmail())) {
            throw new IllegalArgumentException(
                    "An account with this email already exists"
            );
        }

        user.setPassword(
                passwordEncoder.encode(user.getPassword())
        );

        user.setApiKey(generateApiKeyValue());

        return userRepository.save(user);
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User getUserById(Long id) {

        return userRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found with id: " + id
                        )
                );
    }

    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    @Override
    public long getUserCount() {
        return userRepository.count();
    }

    @Override
    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public User loginUser(String email, String password) {

        User user =
                userRepository.findByEmail(email).orElse(null);

        if (user != null
                && passwordEncoder.matches(
                        password,
                        user.getPassword())) {

            return user;
        }

        return null;
    }

    @Override
    public User updateProfile(
            Long userId,
            String username,
            String currentPassword,
            String newPassword) {

        User user = getUserById(userId);

        if (currentPassword == null
                || !passwordEncoder.matches(
                        currentPassword,
                        user.getPassword())) {

            throw new IllegalArgumentException(
                    "Current password is incorrect"
            );
        }

        if (username != null && !username.isBlank()) {
            user.setUsername(username);
        }

        if (newPassword != null && !newPassword.isBlank()) {
            user.setPassword(
                    passwordEncoder.encode(newPassword)
            );
        }

        return userRepository.save(user);
    }

    @Override
    public User updateUsername(Long userId, String username) {

        User user = getUserById(userId);

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Username is required"
            );
        }

        user.setUsername(username);

        return userRepository.save(user);
    }

    @Override
    public String generateApiKey(Long userId) {

        User user = getUserById(userId);

        String apiKey = generateApiKeyValue();

        user.setApiKey(apiKey);
        userRepository.save(user);

        return apiKey;
    }

    @Override
    public String getApiKey(Long userId) {

        User user = getUserById(userId);

        return user.getApiKey();
    }

    private String generateApiKeyValue() {

        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
