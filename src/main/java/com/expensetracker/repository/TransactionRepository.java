package com.expensetracker.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expensetracker.entity.ReviewStatus;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionStatus;
import com.expensetracker.entity.User;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByUserOrderByTransactionDateDesc(User user);

    // Overload with a Pageable so "recent N transactions" respects whatever
    // limit the caller asks for (PageRequest.of(0, limit)) instead of a
    // hardcoded Top-N.
    List<Transaction> findByUserOrderByTransactionDateDesc(User user, Pageable pageable);

    Optional<Transaction> findByExternalTransactionId(String externalTransactionId);

    boolean existsByExternalTransactionId(String externalTransactionId);

    // Same COALESCE(SUM(...), 0) + MONTH()/YEAR() pattern already used by
    // ExpenseRepository.sumAmountByUserAndMonthAndYear, restricted to a
    // single status since only SUCCESS transactions should count toward
    // spending (a FAILED transaction never took money out of the account).
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
           "WHERE t.user = :user AND t.status = :status " +
           "AND MONTH(t.transactionDate) = :month AND YEAR(t.transactionDate) = :year")
    double sumAmountByUserAndStatusAndMonthAndYear(@Param("user") User user,
                                                    @Param("status") TransactionStatus status,
                                                    @Param("month") int month,
                                                    @Param("year") int year);

    // ---- Smart Transaction Review Hub ----

    // The review inbox (NEEDS_REVIEW), and the "history" tabs (ACCEPTED /
    // IGNORED), newest first.
    List<Transaction> findByUserAndReviewStatusOrderByTransactionDateDesc(User user, ReviewStatus reviewStatus);

    long countByUserAndReviewStatus(User user, ReviewStatus reviewStatus);

    // Every transaction that has NOT been ignored, oldest first - the input to
    // RecurringDetectionService (it needs ACCEPTED history plus what is still
    // pending, in chronological order, to spot a repeating series).
    List<Transaction> findByUserAndReviewStatusNotOrderByTransactionDateAsc(User user, ReviewStatus reviewStatus);

    // Candidate window for near-duplicate detection on import (same user, a
    // few days either side of the incoming transaction's date).
    List<Transaction> findByUserAndTransactionDateBetween(User user, LocalDateTime start, LocalDateTime end);

    // "You have N transactions (worth Rs X) waiting to be reviewed" on the
    // dashboard - only SUCCESS + NEEDS_REVIEW in the given month, since that is
    // exactly the amount not yet reflected in the budget.
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t "
         + "WHERE t.user = :user AND t.status = :status AND t.reviewStatus = :reviewStatus "
         + "AND MONTH(t.transactionDate) = :month AND YEAR(t.transactionDate) = :year")
    double sumAmountByUserAndStatusAndReviewStatusAndMonthAndYear(@Param("user") User user,
                                                                   @Param("status") TransactionStatus status,
                                                                   @Param("reviewStatus") ReviewStatus reviewStatus,
                                                                   @Param("month") int month,
                                                                   @Param("year") int year);

    Optional<Transaction> findByIdAndUser(Long id, User user);
}
