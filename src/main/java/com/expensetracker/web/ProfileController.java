package com.expensetracker.web;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.entity.User;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.UserService;

@Controller
public class ProfileController {

    private final UserService userService;
    private final ExpenseService expenseService;
    private final CategoryService categoryService;

    public ProfileController(UserService userService, ExpenseService expenseService, CategoryService categoryService) {
        this.userService = userService;
        this.expenseService = expenseService;
        this.categoryService = categoryService;
    }

    @GetMapping("/profile")
    public String profile(Model model, HttpSession session) {
        User user = CurrentUser.from(session);

        model.addAttribute("user", user);
        model.addAttribute("totalExpenses", expenseService.getExpenseCount(user));
        model.addAttribute("totalCategories", categoryService.getCategoryCount(user));

        return "profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@RequestParam String fullName,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        try {
            User updated = userService.updateFullName(user.getId(), fullName);
            session.setAttribute("loggedInUser", updated);
            redirectAttributes.addFlashAttribute("success", "Profile updated successfully");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }

        return "redirect:/profile";
    }
}
