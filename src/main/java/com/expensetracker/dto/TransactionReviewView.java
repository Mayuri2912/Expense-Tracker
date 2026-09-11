package com.expensetracker.dto;

import com.expensetracker.entity.Transaction;

/**
 * One row of the review inbox: the transaction itself plus everything the UI
 * needs to help the user decide - the category suggestion (already on the
 * entity), a recurring flag (already on the entity), and a possible-duplicate
 * warning computed on the fly.
 */
public class TransactionReviewView {

    private final Transaction transaction;
    private final DuplicateWarning duplicateWarning; // null when nothing looks like a duplicate

    public TransactionReviewView(Transaction transaction, DuplicateWarning duplicateWarning) {
        this.transaction = transaction;
        this.duplicateWarning = duplicateWarning;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public DuplicateWarning getDuplicateWarning() {
        return duplicateWarning;
    }

    public boolean isDuplicateSuspected() {
        return duplicateWarning != null;
    }

    public boolean isRecurring() {
        return transaction.isRecurring();
    }

    public boolean hasSuggestion() {
        return transaction.getSuggestedCategory() != null;
    }
}
