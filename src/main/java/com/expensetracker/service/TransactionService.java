package com.expensetracker.service;

import java.util.List;

import com.expensetracker.dto.BudgetCheckResult;
import com.expensetracker.dto.TransactionOutcome;
import com.expensetracker.dto.TransactionTestRequest;
import com.expensetracker.dto.TransactionWebhookRequest;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.User;

public interface TransactionService {

    /**
     * Processes a transaction delivered by the test/sandbox webhook. The
     * user is identified from the payload's email address, since a real
     * webhook call carries no session.
     */
    TransactionOutcome recordWebhookTransaction(TransactionWebhookRequest request);

    /**
     * Processes a transaction submitted through the manual test endpoint.
     * The user is always the caller's own logged-in session user - never
     * taken from the request body - so this can't be used to impersonate
     * another user.
     */
    TransactionOutcome recordTestTransaction(User user, TransactionTestRequest request);

    List<Transaction> getAllTransactionsForUser(User user);

    List<Transaction> getRecentTransactionsForUser(User user, int limit);

    /** Combined (expenses + SUCCESS transactions) budget status for the current month. */
    BudgetCheckResult checkCurrentMonthBudget(User user);

}
