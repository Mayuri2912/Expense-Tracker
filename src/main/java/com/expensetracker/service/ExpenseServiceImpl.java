package com.expensetracker.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.ExpenseRepository;

@Service
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;

    public ExpenseServiceImpl(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    @Override
    public Expense addExpense(Expense expense, User user) {
        expense.setUser(user);
        return expenseRepository.save(expense);
    }

    @Override
    public Expense updateExpense(Long id, User user, Expense updatedFields) {
        Expense existing = getExpenseByIdForUser(id, user);

        existing.setTitle(updatedFields.getTitle());
        existing.setAmount(updatedFields.getAmount());
        existing.setCategory(updatedFields.getCategory());
        existing.setCategoryId(updatedFields.getCategoryId());
        existing.setDescription(updatedFields.getDescription());
        existing.setPaymentMethod(updatedFields.getPaymentMethod());
        existing.setDate(updatedFields.getDate());
        existing.setSource(updatedFields.getSource());

        return expenseRepository.save(existing);
    }

    @Override
    public List<Expense> getAllExpensesForUser(User user) {
        return expenseRepository.findByUserOrderByDateDesc(user);
    }

    @Override
    public List<Expense> searchExpenses(
            User user,
            String keyword,
            String category,
            String paymentMethod,
            LocalDate startDate,
            LocalDate endDate) {

        String normalizedKeyword =
                (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        String normalizedCategory =
                (category == null || category.isBlank()) ? null : category.trim();
        String normalizedPaymentMethod =
                (paymentMethod == null || paymentMethod.isBlank()) ? null : paymentMethod.trim();

        return expenseRepository.searchExpenses(
                user,
                normalizedKeyword,
                normalizedCategory,
                normalizedPaymentMethod,
                startDate,
                endDate
        );
    }

    @Override
    public Expense getExpenseByIdForUser(Long id, User user) {
        return expenseRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found"));
    }

    @Override
    public void deleteExpense(Long id, User user) {
        Expense expense = getExpenseByIdForUser(id, user);
        expenseRepository.delete(expense);
    }

    @Override
    public long getExpenseCount(User user) {
        return expenseRepository.countByUser(user);
    }

    @Override
    public double getTotalExpenseAmount(User user) {
        return expenseRepository.sumAmountByUser(user);
    }

    @Override
    public double getMonthlyExpenseAmount(User user, int month, int year) {
        return expenseRepository.sumAmountByUserAndMonthAndYear(
                user, month, year
        );
    }

    @Override
    public Map<String, Double> getCategoryWiseTotals(User user) {
        Map<String, Double> totals = new LinkedHashMap<>();

        for (Object[] row : expenseRepository.sumAmountByCategoryForUser(user)) {
            String categoryName =
                    row[0] != null ? (String) row[0] : "Uncategorized";

            Double amount = ((Number) row[1]).doubleValue();

            totals.put(categoryName, amount);
        }

        return totals;
    }

    @Override
    public Map<String, Double> getLastSixMonthsTotals(User user) {
        Map<String, Double> totals = new LinkedHashMap<>();

        DateTimeFormatter labelFormat =
                DateTimeFormatter.ofPattern("MMM yyyy");

        LocalDate cursor =
                LocalDate.now().minusMonths(5).withDayOfMonth(1);

        for (int i = 0; i < 6; i++) {
            double monthTotal =
                    expenseRepository.sumAmountByUserAndMonthAndYear(
                            user,
                            cursor.getMonthValue(),
                            cursor.getYear()
                    );

            totals.put(cursor.format(labelFormat), monthTotal);

            cursor = cursor.plusMonths(1);
        }

        return totals;
    }
}
