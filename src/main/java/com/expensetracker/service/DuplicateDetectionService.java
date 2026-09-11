package com.expensetracker.service;

import java.util.Optional;

import com.expensetracker.dto.DuplicateWarning;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;

/**
 * Fuzzy "have I seen this spend already?" checks. All deterministic - amount
 * closeness, a date window, and shared words between merchant and expense
 * title/description. Nothing here writes to the database.
 */
public interface DuplicateDetectionService {

    /**
     * Does this (usually still-pending) transaction look like an Expense the
     * user already logged by hand? Used to show a warning badge in the review
     * inbox. Returns the best candidate, or empty.
     */
    Optional<DuplicateWarning> checkAgainstExpenses(User user, Transaction txn);

    /**
     * Does an equivalent transaction already exist for this user (same amount,
     * same merchant, within a couple of days)? Used by statement import to
     * skip near-duplicate rows that carry a different reference id than the
     * one already stored.
     */
    Optional<Transaction> findSimilarTransaction(User user, Transaction incoming);
}
