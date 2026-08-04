package com.expensetracker.service;

import java.util.Optional;

import com.expensetracker.entity.Budget;
import com.expensetracker.entity.User;

public interface BudgetService {

    Budget setBudget(User user, double amount, int month, int year);

    Optional<Budget> getBudget(User user, int month, int year);

}
