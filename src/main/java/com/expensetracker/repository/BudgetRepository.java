package com.expensetracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expensetracker.entity.Budget;
import com.expensetracker.entity.User;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    Optional<Budget> findByUserAndMonthAndYear(User user, Integer month, Integer year);

}
