package com.expensetracker.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.expensetracker.dto.AcceptTransactionRequest;
import com.expensetracker.dto.BudgetCheckResult;
import com.expensetracker.dto.RecurringGroup;
import com.expensetracker.dto.StatementImportResult;
import com.expensetracker.dto.TransactionOutcome;
import com.expensetracker.dto.TransactionReviewView;
import com.expensetracker.dto.TransactionTestRequest;
import com.expensetracker.dto.TransactionWebhookRequest;
import com.expensetracker.entity.ReviewStatus;
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

    // ---------------- Smart Transaction Review Hub ----------------

    /** Import a CSV/Excel bank statement as NEEDS_REVIEW transactions. */
    StatementImportResult importStatement(User user, MultipartFile file);

    /** The review inbox: pending transactions with duplicate warnings attached. */
    List<TransactionReviewView> getReviewInbox(User user);

    /** Transactions in a given review state (e.g. ACCEPTED / IGNORED history tabs). */
    List<Transaction> getTransactionsByReviewStatus(User user, ReviewStatus status);

    long countByReviewStatus(User user, ReviewStatus status);

    /** SUCCESS + NEEDS_REVIEW spend in the current month - the amount not yet in the budget. */
    double getPendingReviewAmountThisMonth(User user);

    /** Currently detected recurring series for this user (read-only). */
    List<RecurringGroup> getRecurringGroups(User user);

    /**
     * Accept a reviewed transaction: create a real Expense from it (via the
     * existing ExpenseService), link the two, mark the transaction ACCEPTED,
     * and optionally save a personal categorization rule. Nothing enters the
     * expenses table without this call.
     */
    Transaction acceptTransaction(Long transactionId, User user, AcceptTransactionRequest request);

    /** Dismiss a reviewed transaction; it is never counted anywhere. */
    Transaction ignoreTransaction(Long transactionId, User user);

    /** Move an ignored transaction back into the review inbox. */
    Transaction moveBackToReview(Long transactionId, User user);

}
