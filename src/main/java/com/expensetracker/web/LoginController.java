package com.expensetracker.web;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.expensetracker.entity.User;
import com.expensetracker.service.UserService;

@Controller
public class LoginController {

    private final UserService userService;

    public LoginController(UserService userService) {
        this.userService = userService;
    }

    // LOGIN
    @PostMapping("/login")
    public String login(@RequestParam String email,
                         @RequestParam String password,
                         HttpSession session,
                         Model model) {

        User user = userService.loginUser(email, password);

        if (user != null) {
            session.setAttribute("loggedInUser", user);
            return "redirect:/dashboard";
        }

        model.addAttribute("error", "Invalid email or password");
        return "login";
    }

    // REGISTER
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("user") User user,
                            BindingResult bindingResult,
                            @RequestParam(name = "confirmPassword", required = false) String confirmPassword,
                            Model model) {

        if (bindingResult.hasErrors()) {
            return "register";
        }

        if (confirmPassword == null || !confirmPassword.equals(user.getPassword())) {
            model.addAttribute("error", "Passwords do not match");
            return "register";
        }

        try {
            userService.registerUser(user);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "register";
        }

        model.addAttribute("success", "Registration successful. Please login.");
        return "login";
    }
}
