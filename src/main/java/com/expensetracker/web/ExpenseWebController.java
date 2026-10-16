package com.expensetracker.web;

import java.time.LocalDate;
import java.util.List;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;

@Controller
public class ExpenseWebController {

    private final ExpenseService expenseService;
    private final CategoryService categoryService;

    public ExpenseWebController(ExpenseService expenseService,
                                CategoryService categoryService) {
        this.expenseService = expenseService;
        this.categoryService = categoryService;
    }

    // ---------------- ADD EXPENSE ----------------

    @GetMapping("/add-expense")
    public String addExpenseForm(Model model, HttpSession session) {
        User user = CurrentUser.from(session);

        model.addAttribute("expense", new Expense());
        model.addAttribute(
                "categories",
                categoryService.getAllCategoriesForUser(user)
        );

        return "add-expense";
    }

    @PostMapping("/save-expense")
    public String saveExpense(
            @Valid @ModelAttribute("expense") Expense expense,
            BindingResult bindingResult,
            @RequestParam(required = false) Long categoryId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        if (categoryId == null) {
            bindingResult.rejectValue(
                    "category",
                    "required",
                    "Please select a category"
            );
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute(
                    "categories",
                    categoryService.getAllCategoriesForUser(user)
            );
            return "add-expense";
        }

        Category category =
                categoryService.getCategoryByIdForUser(categoryId, user);

        expense.setCategory(category.getName());
        expense.setCategoryId(category.getId());

        expenseService.addExpense(expense, user);

        redirectAttributes.addFlashAttribute(
                "success",
                "Expense added successfully"
        );

        return "redirect:/dashboard";
    }

    // ---------------- EDIT EXPENSE ----------------

    @GetMapping("/edit-expense/{id}")
    public String editExpenseForm(
            @PathVariable Long id,
            Model model,
            HttpSession session) {

        User user = CurrentUser.from(session);

        Expense expense =
                expenseService.getExpenseByIdForUser(id, user);

        model.addAttribute("expense", expense);
        model.addAttribute(
                "categories",
                categoryService.getAllCategoriesForUser(user)
        );

        return "edit-expense";
    }

    @PostMapping("/update-expense")
    public String updateExpense(
            @Valid @ModelAttribute("expense") Expense expense,
            BindingResult bindingResult,
            @RequestParam(required = false) Long categoryId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        if (categoryId == null) {
            bindingResult.rejectValue(
                    "category",
                    "required",
                    "Please select a category"
            );
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute(
                    "categories",
                    categoryService.getAllCategoriesForUser(user)
            );
            return "edit-expense";
        }

        Category category =
                categoryService.getCategoryByIdForUser(categoryId, user);

        expense.setCategory(category.getName());
        expense.setCategoryId(category.getId());

        expenseService.updateExpense(
                expense.getId(),
                user,
                expense
        );

        redirectAttributes.addFlashAttribute(
                "success",
                "Expense updated successfully"
        );

        return "redirect:/dashboard";
    }

    // ---------------- DELETE ----------------

    @PostMapping("/delete-expense/{id}")
    public String deleteExpense(
            @PathVariable Long id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        expenseService.deleteExpense(id, user);

        redirectAttributes.addFlashAttribute(
                "success",
                "Expense deleted successfully"
        );

        return "redirect:/expenses-page";
    }

    // ---------------- EXPENSE LIST + SEARCH/FILTER ----------------

    @GetMapping("/expenses-page")
    public String expenses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String paymentMethod,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,
            Model model,
            HttpSession session) {

        User user = CurrentUser.from(session);

        String categoryName = null;

        if (categoryId != null) {
            try {
                Category category =
                        categoryService.getCategoryByIdForUser(
                                categoryId,
                                user
                        );

                if (category != null) {
                    categoryName = category.getName();
                }
            } catch (Exception ignored) {
                categoryName = null;
            }
        }

        List<Expense> expenses = expenseService.searchExpenses(
                user,
                keyword,
                categoryName,
                paymentMethod,
                startDate,
                endDate
        );

        model.addAttribute("expenses", expenses);
        model.addAttribute(
                "categories",
                categoryService.getAllCategoriesForUser(user)
        );

        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("paymentMethod", paymentMethod);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "expenses";
    }
}
