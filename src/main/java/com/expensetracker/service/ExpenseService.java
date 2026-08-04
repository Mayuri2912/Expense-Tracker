package com.expensetracker.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;

public interface ExpenseService {

    Expense addExpense(Expense expense, User user);

    Expense updateExpense(Long id, User user, Expense updatedFields);

    List<Expense> getAllExpensesForUser(User user);

    List<Expense> searchExpenses(User user, String keyword, Long categoryId,
                                  String paymentMethod, LocalDate startDate, LocalDate endDate);

    Expense getExpenseByIdForUser(Long id, User user);

    void deleteExpense(Long id, User user);

    long getExpenseCount(User user);

    /** Used to warn before deleting a category, since deleting it cascades to its expenses. */
    long getExpenseCountForCategory(Category category);

    double getTotalExpenseAmount(User user);

    double getMonthlyExpenseAmount(User user, int month, int year);

    /** Category name -> total spent, used for the dashboard pie chart. */
    Map<String, Double> getCategoryWiseTotals(User user);

    /** "MMM yyyy" label -> total spent, for the last 6 months, used for the dashboard bar chart. */
    Map<String, Double> getLastSixMonthsTotals(User user);

}
