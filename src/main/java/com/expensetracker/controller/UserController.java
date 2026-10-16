package com.expensetracker.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.expensetracker.entity.User;
import com.expensetracker.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers(HttpSession session) {

        if (session.getAttribute("loggedInUser") == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Login required"
            );
        }

        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PostMapping
    public ResponseEntity<User> registerUser(
            @RequestBody User user) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userService.registerUser(user));
    }

    @PostMapping("/login")
    public ResponseEntity<User> loginUser(
            @RequestBody User user,
            HttpSession session) {

        User result = userService.loginUser(
                user.getEmail(),
                user.getPassword()
        );

        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid email or password"
            );
        }

        session.setAttribute("loggedInUser", result);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/api-key")
    public ResponseEntity<String> getApiKey(
            @PathVariable Long id,
            HttpSession session) {

        User loggedInUser =
                (User) session.getAttribute("loggedInUser");

        if (loggedInUser == null
                || !loggedInUser.getId().equals(id)) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Login required"
            );
        }

        return ResponseEntity.ok(
                userService.getApiKey(id)
        );
    }

    @PostMapping("/{id}/api-key/regenerate")
    public ResponseEntity<String> regenerateApiKey(
            @PathVariable Long id,
            HttpSession session) {

        User loggedInUser =
                (User) session.getAttribute("loggedInUser");

        if (loggedInUser == null
                || !loggedInUser.getId().equals(id)) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Login required"
            );
        }

        String newApiKey =
                userService.generateApiKey(id);

        return ResponseEntity.ok(newApiKey);
    }
}