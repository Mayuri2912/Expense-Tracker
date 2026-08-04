package com.expensetracker.web;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.entity.User;
import com.expensetracker.service.UserService;

@Controller
public class SettingsController {

    private final UserService userService;

    @Value("${application.version}")
    private String applicationVersion;

    public SettingsController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("applicationVersion", applicationVersion);
        return "settings";
    }

    @GetMapping("/security")
    public String security() {
        return "security";
    }

    @PostMapping("/settings/change-password")
    public String changePassword(@RequestParam String currentPassword,
                                  @RequestParam String newPassword,
                                  @RequestParam String confirmPassword,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "New password and confirmation do not match");
            return "redirect:/security";
        }

        User user = CurrentUser.from(session);

        try {
            User updated = userService.updateProfile(user.getId(), null, currentPassword, newPassword);
            session.setAttribute("loggedInUser", updated);
            redirectAttributes.addFlashAttribute("success", "Password changed successfully");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/security";
    }
}
