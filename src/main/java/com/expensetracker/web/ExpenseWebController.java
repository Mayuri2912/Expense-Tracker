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

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;

@Controller
public class ExpenseWebController {

    private static final List<String> PAYMENT_METHODS = List.of("Cash", "UPI", "Card", "Net Banking");

    private final ExpenseService expenseService;
    private final CategoryService categoryService;

    public ExpenseWebController(ExpenseService expenseService, CategoryService categoryService) {
        this.expenseService = expenseService;
        this.categoryService = categoryService;
    }

    // ---------------- ADD EXPENSE ----------------

    @GetMapping("/add-expense")
    public String addExpenseForm(Model model, HttpSession session) {
        model.addAttribute("expense", new Expense());
        model.addAttribute("categories", categoryService.getAllCategoriesForUser(CurrentUser.from(session)));
        model.addAttribute("paymentMethods", PAYMENT_METHODS);
        return "add-expense";
    }

    @PostMapping("/save-expense")
    public String saveExpense(@Valid @ModelAttribute("expense") Expense expense,
                               BindingResult bindingResult,
                               @RequestParam(required = false) Long categoryId,
                               HttpSession session,
                               Model model,
                               RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        // The database requires every expense to have a category (category_id is
        // NOT NULL), so this must be validated before we ever attempt to save.
        if (categoryId == null) {
            bindingResult.rejectValue("category", "required", "Please select a category");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryService.getAllCategoriesForUser(user));
            model.addAttribute("paymentMethods", PAYMENT_METHODS);
            return "add-expense";
        }

        // Look the category up server-side (rather than trusting a bound object)
        // so a tampered categoryId can never attach another user's category.
        expense.setCategory(categoryService.getCategoryByIdForUser(categoryId, user));

        expenseService.addExpense(expense, user);
        redirectAttributes.addFlashAttribute("success", "Expense added successfully");
        return "redirect:/dashboard";
    }

    // ---------------- EDIT EXPENSE ----------------

    @GetMapping("/edit-expense/{id}")
    public String editExpenseForm(@PathVariable Long id, Model model, HttpSession session) {
        User user = CurrentUser.from(session);
        model.addAttribute("expense", expenseService.getExpenseByIdForUser(id, user));
        model.addAttribute("categories", categoryService.getAllCategoriesForUser(user));
        model.addAttribute("paymentMethods", PAYMENT_METHODS);
        return "edit-expense";
    }

    @PostMapping("/update-expense")
    public String updateExpense(@Valid @ModelAttribute("expense") Expense expense,
                                 BindingResult bindingResult,
                                 @RequestParam(required = false) Long categoryId,
                                 HttpSession session,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        User user = CurrentUser.from(session);

        if (categoryId == null) {
            bindingResult.rejectValue("category", "required", "Please select a category");
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", categoryService.getAllCategoriesForUser(user));
            model.addAttribute("paymentMethods", PAYMENT_METHODS);
            return "edit-expense";
        }

        expense.setCategory(categoryService.getCategoryByIdForUser(categoryId, user));

        expenseService.updateExpense(expense.getId(), user, expense);
        redirectAttributes.addFlashAttribute("success", "Expense updated successfully");
        return "redirect:/dashboard";
    }

    // ---------------- DELETE ----------------

    @PostMapping("/delete-expense/{id}")
    public String deleteExpense(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        expenseService.deleteExpense(id, CurrentUser.from(session));
        redirectAttributes.addFlashAttribute("success", "Expense deleted successfully");
        return "redirect:/expenses-page";
    }

    // ---------------- EXPENSE LIST + SEARCH/FILTER ----------------

    @GetMapping("/expenses-page")
    public String expenses(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) Long categoryId,
                            @RequestParam(required = false) String paymentMethod,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                            Model model,
                            HttpSession session) {

        User user = CurrentUser.from(session);

        List<Expense> expenses = expenseService.searchExpenses(
                user, keyword, categoryId, paymentMethod, startDate, endDate);

        model.addAttribute("expenses", expenses);
        model.addAttribute("categories", categoryService.getAllCategoriesForUser(user));
        model.addAttribute("paymentMethods", PAYMENT_METHODS);

        // Echo filters back so the form stays populated after searching.
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("paymentMethod", paymentMethod);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "expenses";
    }
}
