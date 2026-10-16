package com.expensetracker.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.entity.Category;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.ExpenseService;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CategoryService categoryService;

    public ExpenseController(ExpenseService expenseService, CategoryService categoryService) {
        this.expenseService = expenseService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<Expense>> getAllExpenses(HttpSession session) {
        User user = requireUser(session);
        return ResponseEntity.ok(expenseService.getAllExpensesForUser(user));
    }

    @PostMapping
    public ResponseEntity<Expense> addExpense(@RequestBody Expense expense, HttpSession session) {
        User user = requireUser(session);
        if (expense.getCategoryId() == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Category is required");
        }
        Category category = categoryService.getCategoryByIdForUser(expense.getCategoryId(), user);
        expense.setCategory(category.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(expenseService.addExpense(expense, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id, HttpSession session) {
        User user = requireUser(session);
        expenseService.deleteExpense(id, user);
        return ResponseEntity.noContent().build();
    }

    private User requireUser(HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return user;
    }
}
