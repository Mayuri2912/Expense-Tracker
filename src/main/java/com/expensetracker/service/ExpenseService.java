package com.expensetracker.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;

public interface ExpenseService {

    Expense addExpense(Expense expense, User user);

    Expense updateExpense(Long id, User user, Expense updatedFields);

    List<Expense> getAllExpensesForUser(User user);

    List<Expense> searchExpenses(
            User user,
            String keyword,
            String category,
            String paymentMethod,
            LocalDate startDate,
            LocalDate endDate
    );

    Expense getExpenseByIdForUser(Long id, User user);

    void deleteExpense(Long id, User user);

    long getExpenseCount(User user);

    double getTotalExpenseAmount(User user);

    double getMonthlyExpenseAmount(
            User user,
            int month,
            int year
    );

    Map<String, Double> getCategoryWiseTotals(User user);

    Map<String, Double> getLastSixMonthsTotals(User user);
}
