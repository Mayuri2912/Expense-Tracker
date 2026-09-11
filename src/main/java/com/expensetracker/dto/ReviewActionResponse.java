package com.expensetracker.dto;

import com.expensetracker.entity.Transaction;

/**
 * Small JSON body returned by the REST accept/ignore endpoints so an API
 * caller can see what happened without re-fetching the transaction.
 */
public class ReviewActionResponse {

    private final String transactionId;
    private final String reviewStatus;
    private final Long expenseId;
    private final String message;

    private ReviewActionResponse(String transactionId, String reviewStatus, Long expenseId, String message) {
        this.transactionId = transactionId;
        this.reviewStatus = reviewStatus;
        this.expenseId = expenseId;
        this.message = message;
    }

    public static ReviewActionResponse from(Transaction txn, String message) {
        return new ReviewActionResponse(
                txn.getExternalTransactionId(),
                txn.getReviewStatus() == null ? null : txn.getReviewStatus().name(),
                txn.getExpense() == null ? null : txn.getExpense().getId(),
                message);
    }

    public String getTransactionId() { return transactionId; }
    public String getReviewStatus() { return reviewStatus; }
    public Long getExpenseId() { return expenseId; }
    public String getMessage() { return message; }
}
