package com.expensetracker.service;

import java.util.List;

import com.expensetracker.dto.RecurringGroup;
import com.expensetracker.entity.User;

/**
 * Finds repeating payment series (subscriptions, rent, EMIs) among a user's
 * non-ignored transactions: same merchant, ~same amount, ~regular interval.
 * Purely statistical - no external data.
 */
public interface RecurringDetectionService {

    /**
     * Recomputes and PERSISTS {@code recurring} / {@code recurringGroupKey} on
     * every one of the user's non-ignored transactions. Call after import and
     * after an accept/ignore changes the picture.
     *
     * @return number of distinct recurring series found
     */
    int recomputeForUser(User user);

    /** Read-only view of the current recurring series, biggest monthly cost first. */
    List<RecurringGroup> summarizeForUser(User user);
}
