package com.expensetracker.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.User;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;

@Controller
public class CategoryWebController {

    private final CategoryService categoryService;
    private final ExpenseService expenseService;

    public CategoryWebController(CategoryService categoryService, ExpenseService expenseService) {
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
    public String saveCategory(@Valid @ModelAttribute("category") Category category,
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

        redirectAttributes.addFlashAttribute("success", "Category added successfully");
        return "redirect:/categories-page";
    }

    // ---------------- EDIT CATEGORY ----------------

    @GetMapping("/edit-category/{id}")
    public String editCategoryForm(@PathVariable Long id, Model model, HttpSession session) {
        model.addAttribute("category", categoryService.getCategoryByIdForUser(id, CurrentUser.from(session)));
        return "edit-category";
    }

    @PostMapping("/update-category")
    public String updateCategory(@RequestParam Long id,
                                  @RequestParam String name,
                                  HttpSession session,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        if (name == null || name.isBlank()) {
            model.addAttribute("category", categoryService.getCategoryByIdForUser(id, user));
            model.addAttribute("error", "Category name is required");
            return "edit-category";
        }

        try {
            categoryService.updateCategory(id, user, name.trim());
        } catch (IllegalArgumentException ex) {
            model.addAttribute("category", categoryService.getCategoryByIdForUser(id, user));
            model.addAttribute("error", ex.getMessage());
            return "edit-category";
        }

        redirectAttributes.addFlashAttribute("success", "Category updated successfully");
        return "redirect:/categories-page";
    }

    // ---------------- DELETE ----------------

    // NOTE: The database's expenses.category_id foreign key uses ON DELETE
    // CASCADE, so deleting a category also permanently deletes every expense
    // that references it - MySQL does this silently, it does not raise a
    // constraint violation. The confirmation modal on the categories page
    // warns about this before the request is ever sent.
    @PostMapping("/delete-category/{id}")
    public String deleteCategory(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = CurrentUser.from(session);
        Category category = categoryService.getCategoryByIdForUser(id, user);
        long linkedExpenses = expenseService.getExpenseCountForCategory(category);

        categoryService.deleteCategory(id, user);

        String message = linkedExpenses > 0
                ? "Category deleted, along with " + linkedExpenses + " linked expense(s)."
                : "Category deleted successfully";
        redirectAttributes.addFlashAttribute("success", message);
        return "redirect:/categories-page";
    }

    // ---------------- LIST + SEARCH ----------------

    @GetMapping("/categories-page")
    public String categories(@RequestParam(required = false) String keyword, Model model, HttpSession session) {
        User user = CurrentUser.from(session);
        List<Category> categories = categoryService.searchCategories(user, keyword);

        Map<Long, Long> expenseCounts = new HashMap<>();
        for (Category category : categories) {
            expenseCounts.put(category.getId(), expenseService.getExpenseCountForCategory(category));
        }

        model.addAttribute("categories", categories);
        model.addAttribute("expenseCounts", expenseCounts);
        model.addAttribute("keyword", keyword);
        return "categories";
    }
}
