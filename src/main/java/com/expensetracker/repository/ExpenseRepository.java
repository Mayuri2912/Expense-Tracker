package com.expensetracker.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expensetracker.entity.Category;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.User;

// JpaSpecificationExecutor backs the dynamic search/filter query in
// ExpenseServiceImpl.searchExpenses (built with a Specification instead of a
// static "@Query ... :param IS NULL OR ..." string). That older pattern is
// known to be unreliable across Hibernate/Spring Data versions when an
// optional parameter is also used inside a function call such as LIKE/CONCAT:
// Hibernate can bind the "null" value in a way that no longer satisfies its
// own "IS NULL" check, so the filter silently matches nothing instead of
// "everything" as intended - see spring-data-jpa issues #2570, #2683, #3205.
// A Specification sidesteps this entirely: a clause for an absent filter is
// never added to the query at all, so there's no null-through-a-function
// binding to get wrong.
public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByUserOrderByExpenseDateDesc(User user);

    Optional<Expense> findByIdAndUser(Long id, User user);

    long countByUser(User user);

    long countByCategory(Category category);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e "
         + "WHERE e.user = :user AND MONTH(e.expenseDate) = :month AND YEAR(e.expenseDate) = :year")
    double sumAmountByUserAndMonthAndYear(@Param("user") User user,
                                          @Param("month") int month,
                                          @Param("year") int year);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e WHERE e.user = :user")
    double sumAmountByUser(@Param("user") User user);

    @Query("SELECT e.category.name, SUM(e.amount) FROM Expense e "
         + "WHERE e.user = :user GROUP BY e.category.name ORDER BY SUM(e.amount) DESC")
    List<Object[]> sumAmountByCategoryForUser(@Param("user") User user);

}
