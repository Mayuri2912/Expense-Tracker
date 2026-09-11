package com.expensetracker.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;

import com.expensetracker.entity.Category;
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
        existing.setDescription(updatedFields.getDescription());
        existing.setExpenseDate(updatedFields.getExpenseDate());
        existing.setPaymentMethod(updatedFields.getPaymentMethod());
        existing.setCategory(updatedFields.getCategory());

        return expenseRepository.save(existing);
    }

    @Override
    public List<Expense> getAllExpensesForUser(User user) {
        return expenseRepository.findByUserOrderByExpenseDateDesc(user);
    }

    @Override
    public List<Expense> searchExpenses(User user, String keyword, Long categoryId,
                                         String paymentMethod, LocalDate startDate, LocalDate endDate) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase();
        String normalizedPayment = (paymentMethod == null || paymentMethod.isBlank()) ? null : paymentMethod;

        // Built as a Specification (rather than a static "@Query ... :param IS
        // NULL OR ..." JPQL string) so that a clause for an absent filter is
        // never added to the query at all - see the note on ExpenseRepository
        // for why the old pattern could silently return zero rows.
        Specification<Expense> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user"), user));

            if (normalizedKeyword != null) {
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + normalizedKeyword + "%"));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (normalizedPayment != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), normalizedPayment));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("expenseDate"), endDate));
            }

            query.orderBy(cb.desc(root.get("expenseDate")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return expenseRepository.findAll(spec);
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
    public long getExpenseCountForCategory(Category category) {
        return expenseRepository.countByCategory(category);
    }

    @Override
    public double getTotalExpenseAmount(User user) {
        return expenseRepository.sumAmountByUser(user);
    }

    @Override
    public double getMonthlyExpenseAmount(User user, int month, int year) {
        return expenseRepository.sumAmountByUserAndMonthAndYear(user, month, year);
    }

    @Override
    public Map<String, Double> getCategoryWiseTotals(User user) {
        Map<String, Double> totals = new LinkedHashMap<>();
        for (Object[] row : expenseRepository.sumAmountByCategoryForUser(user)) {
            String categoryName = row[0] != null ? (String) row[0] : "Uncategorized";
            Double amount = (Double) row[1];
            totals.put(categoryName, amount);
        }
        return totals;
    }

    @Override
    public Map<String, Double> getLastSixMonthsTotals(User user) {
        Map<String, Double> totals = new LinkedHashMap<>();
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MMM yyyy");

        LocalDate cursor = LocalDate.now().minusMonths(5).withDayOfMonth(1);

        for (int i = 0; i < 6; i++) {
            double monthTotal = expenseRepository.sumAmountByUserAndMonthAndYear(
                    user, cursor.getMonthValue(), cursor.getYear());
            totals.put(cursor.format(labelFormat), monthTotal);
            cursor = cursor.plusMonths(1);
        }

        return totals;
    }
}
