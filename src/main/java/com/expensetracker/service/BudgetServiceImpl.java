package com.expensetracker.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.expensetracker.entity.Budget;
import com.expensetracker.entity.User;
import com.expensetracker.repository.BudgetRepository;

@Service
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRepository budgetRepository;

    public BudgetServiceImpl(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Override
    public Budget setBudget(User user, double amount, int month, int year) {
        Budget budget = budgetRepository.findByUserAndMonthAndYear(user, month, year)
                .orElseGet(Budget::new);

        budget.setUser(user);
        budget.setMonth(month);
        budget.setYear(year);
        budget.setAmount(amount);

        return budgetRepository.save(budget);
    }

    @Override
    public Optional<Budget> getBudget(User user, int month, int year) {
        return budgetRepository.findByUserAndMonthAndYear(user, month, year);
    }
}
