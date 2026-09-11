package com.expensetracker.dto;

import com.expensetracker.entity.Transaction;

/**
 * What TransactionServiceImpl hands back to a controller after processing a
 * webhook or test transaction: the stored Transaction row, whether this
 * delivery was a duplicate of one already recorded, and the resulting
 * budget picture for that transaction's month.
 */
public class TransactionOutcome {

    private final Transaction transaction;
    private final boolean duplicate;
    private final BudgetCheckResult budgetCheck;

    public TransactionOutcome(Transaction transaction, boolean duplicate, BudgetCheckResult budgetCheck) {
        this.transaction = transaction;
        this.duplicate = duplicate;
        this.budgetCheck = budgetCheck;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public BudgetCheckResult getBudgetCheck() {
        return budgetCheck;
    }
}
