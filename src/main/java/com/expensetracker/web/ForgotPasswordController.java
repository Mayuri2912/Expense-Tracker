package com.expensetracker.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.service.PasswordResetService;

@Controller
public class ForgotPasswordController {

    private final PasswordResetService passwordResetService;

    public ForgotPasswordController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // ---------------- STEP 1: request an OTP ----------------

    @GetMapping("/forgot-password")
    public String forgotPasswordForm() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestOtp(@RequestParam String email, RedirectAttributes redirectAttributes) {
        passwordResetService.requestOtp(email);

        // Generic message regardless of whether the email exists, so the
        // form can't be used to discover which addresses are registered.
        redirectAttributes.addFlashAttribute("success",
                "If an account exists for that email, an OTP has been sent. Check your inbox.");
        redirectAttributes.addAttribute("email", email);
        return "redirect:/reset-password";
    }

    // ---------------- STEP 2: verify OTP + set new password ----------------

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam(required = false) String email, Model model) {
        model.addAttribute("email", email);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String email,
                                 @RequestParam String otp,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            model.addAttribute("email", email);
            return "reset-password";
        }

        if (newPassword.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters");
            model.addAttribute("email", email);
            return "reset-password";
        }

        try {
            passwordResetService.resetPassword(email, otp, newPassword);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("email", email);
            return "reset-password";
        }

        redirectAttributes.addFlashAttribute("success", "Password reset successfully. Please log in.");
        return "redirect:/login";
    }
}
