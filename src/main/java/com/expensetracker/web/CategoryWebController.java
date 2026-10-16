package com.expensetracker.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;

@Controller
public class CategoryWebController {

    private final CategoryService categoryService;
    private final ExpenseService expenseService;

    public CategoryWebController(CategoryService categoryService,
                                 ExpenseService expenseService) {
        this.categoryService = categoryService;
        this.expenseService = expenseService;
    }

    // ---------------- ADD CATEGORY ----------------

    @GetMapping("/add-category")
    public String addCategoryForm(Model model) {
        model.addAttribute("category", new Category());
        return "add-category";
    }

    @PostMapping("/save-category")
    public String saveCategory(
            @Valid @ModelAttribute("category") Category category,
            BindingResult bindingResult,
            HttpSession session,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            return "add-category";
        }

        User user = CurrentUser.from(session);

        try {
            categoryService.addCategory(category, user);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "add-category";
        }

        redirectAttributes.addFlashAttribute(
                "success",
                "Category added successfully"
        );

        return "redirect:/categories-page";
    }

    // ---------------- EDIT CATEGORY ----------------

    @GetMapping("/edit-category/{id}")
    public String editCategoryForm(
            @PathVariable Long id,
            Model model,
            HttpSession session) {

        User user = CurrentUser.from(session);

        model.addAttribute(
                "category",
                categoryService.getCategoryByIdForUser(id, user)
        );

        return "edit-category";
    }

    @PostMapping("/update-category")
    public String updateCategory(
            @RequestParam Long id,
            @RequestParam String name,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        if (name == null || name.isBlank()) {
            model.addAttribute(
                    "category",
                    categoryService.getCategoryByIdForUser(id, user)
            );

            model.addAttribute(
                    "error",
                    "Category name is required"
            );

            return "edit-category";
        }

        try {
            categoryService.updateCategory(
                    id,
                    user,
                    name.trim()
            );
        } catch (IllegalArgumentException ex) {
            model.addAttribute(
                    "category",
                    categoryService.getCategoryByIdForUser(id, user)
            );

            model.addAttribute(
                    "error",
                    ex.getMessage()
            );

            return "edit-category";
        }

        redirectAttributes.addFlashAttribute(
                "success",
                "Category updated successfully"
        );

        return "redirect:/categories-page";
    }

    // ---------------- DELETE CATEGORY ----------------

    @PostMapping("/delete-category/{id}")
    public String deleteCategory(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        categoryService.deleteCategory(id, user);

        redirectAttributes.addFlashAttribute(
                "success",
                "Category deleted successfully"
        );

        return "redirect:/categories-page";
    }

    // ---------------- LIST + SEARCH ----------------

    @GetMapping("/categories-page")
    public String categories(
            @RequestParam(required = false) String keyword,
            Model model,
            HttpSession session) {

        User user = CurrentUser.from(session);

        List<Category> categories =
                categoryService.searchCategories(user, keyword);

        Map<Long, Long> expenseCounts = new HashMap<>();

        for (Category category : categories) {

            long count = expenseService
                    .getAllExpensesForUser(user)
                    .stream()
                    .filter(expense ->
                            category.getId().equals(expense.getCategoryId()))
                    .count();

            expenseCounts.put(
                    category.getId(),
                    count
            );
        }

        model.addAttribute("categories", categories);
        model.addAttribute("expenseCounts", expenseCounts);
        model.addAttribute("keyword", keyword);

        return "categories";
    }
}
