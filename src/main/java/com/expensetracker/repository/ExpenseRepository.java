package com.expensetracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByUserOrderByDateDesc(User user);

    Optional<Expense> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.user = :user
    """)
    double sumAmountByUser(@Param("user") User user);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.user = :user
        AND MONTH(e.date) = :month
        AND YEAR(e.date) = :year
    """)
    double sumAmountByUserAndMonthAndYear(
            @Param("user") User user,
            @Param("month") int month,
            @Param("year") int year
    );

    @Query("""
        SELECT e.categoryRecord.name, SUM(e.amount)
        FROM Expense e
        WHERE e.user = :user
        GROUP BY e.categoryRecord.name
        ORDER BY SUM(e.amount) DESC
    """)
    List<Object[]> sumAmountByCategoryForUser(@Param("user") User user);

    @Query("""
        SELECT e
        FROM Expense e
        WHERE e.user = :user
        AND (
            :keyword IS NULL
            OR LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(e.categoryRecord.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
        AND (
            :category IS NULL
            OR e.categoryRecord.name = :category
        )
        AND (
            :paymentMethod IS NULL
            OR e.paymentMethod = :paymentMethod
        )
        AND (
            :startDate IS NULL
            OR e.date >= :startDate
        )
        AND (
            :endDate IS NULL
            OR e.date <= :endDate
        )
        ORDER BY e.date DESC
    """)
    List<Expense> searchExpenses(
            @Param("user") User user,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("paymentMethod") String paymentMethod,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
