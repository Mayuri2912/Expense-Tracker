package com.expensetracker.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.expensetracker.dto.BudgetCheckResult;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;
import com.expensetracker.service.TransactionService;
import com.expensetracker.service.UserService;

@Controller
public class PageController {

    private final UserService userService;
    private final ExpenseService expenseService;
    private final CategoryService categoryService;
    private final BudgetService budgetService;
    private final TransactionService transactionService;

    public PageController(UserService userService, ExpenseService expenseService,
                           CategoryService categoryService, BudgetService budgetService,
                           TransactionService transactionService) {
        this.userService = userService;
        this.expenseService = expenseService;
        this.categoryService = categoryService;
        this.budgetService = budgetService;
        this.transactionService = transactionService;
    }

    // ---------------- HOME ----------------

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    // ---------------- DASHBOARD ----------------

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {

        User user = CurrentUser.from(session);
        LocalDate today = LocalDate.now();

        List<Expense> allExpenses = expenseService.getAllExpensesForUser(user);
        List<Expense> recentExpenses = allExpenses.stream().limit(5).toList();
        double todaysExpense = allExpenses.stream()
                .filter(e -> today.equals(e.getExpenseDate()))
                .mapToDouble(Expense::getAmount)
                .sum();

        model.addAttribute("loggedInUser", user);
        model.addAttribute("totalUsers", userService.getUserCount());
        model.addAttribute("totalCategories", categoryService.getCategoryCount(user));
        model.addAttribute("totalExpenses", expenseService.getExpenseCount(user));
        model.addAttribute("recentExpenses", recentExpenses);
        model.addAttribute("todaysExpense", todaysExpense);

        // ---- Monthly budget & alerts ----
        int month = today.getMonthValue();
        int year = today.getYear();

        double monthlyExpense = expenseService.getMonthlyExpenseAmount(user, month, year);
        Optional<Budget> budgetOpt = budgetService.getBudget(user, month, year);
        double budgetAmount = budgetOpt.map(Budget::getAmount).orElse(0.0);
        double remaining = budgetAmount - monthlyExpense;
        double percentUsed = budgetAmount > 0 ? (monthlyExpense / budgetAmount) * 100.0 : 0.0;

        String budgetStatus = "none";
        if (budgetOpt.isPresent()) {
            if (monthlyExpense > budgetAmount) {
                budgetStatus = "danger";
            } else if (percentUsed >= 80) {
                budgetStatus = "warning";
            } else {
                budgetStatus = "success";
            }
        }

        model.addAttribute("hasBudget", budgetOpt.isPresent());
        model.addAttribute("budgetAmount", budgetAmount);
        model.addAttribute("monthlyExpense", monthlyExpense);
        model.addAttribute("remainingBudget", remaining);
        model.addAttribute("budgetPercentUsed", Math.min(percentUsed, 100.0));
        model.addAttribute("budgetPercentRaw", percentUsed);
        model.addAttribute("budgetStatus", budgetStatus);

        // ---- Chart data ----
        Map<String, Double> categoryTotals = expenseService.getCategoryWiseTotals(user);
        Map<String, Double> monthlyTotals = expenseService.getLastSixMonthsTotals(user);

        model.addAttribute("categoryLabels", new ArrayList<>(categoryTotals.keySet()));
        model.addAttribute("categoryData", new ArrayList<>(categoryTotals.values()));
        model.addAttribute("monthlyLabels", new ArrayList<>(monthlyTotals.keySet()));
        model.addAttribute("monthlyData", new ArrayList<>(monthlyTotals.values()));

        // ---- Online transactions (kept separate from the Expense-only
        // budget card above; see TransactionServiceImpl.evaluateBudget for
        // why online transactions are combined with expenses here) ----
        model.addAttribute("recentTransactions", transactionService.getRecentTransactionsForUser(user, 5));

        BudgetCheckResult txnBudgetCheck = transactionService.checkCurrentMonthBudget(user);
        model.addAttribute("txnBudgetStatus", txnBudgetCheck.getStatus());
        model.addAttribute("txnBudgetMessage", txnBudgetCheck.getMessage());
        model.addAttribute("txnCombinedTotal", txnBudgetCheck.getCombinedTotal());

        return "dashboard";
    }

    // ---------------- LOGOUT ----------------

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
