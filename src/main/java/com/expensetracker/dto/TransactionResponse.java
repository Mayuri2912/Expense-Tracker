package com.expensetracker.dto;

import java.time.LocalDateTime;

/**
 * JSON shape returned by both POST /api/transactions/webhook and
 * POST /api/transactions/test - built from a TransactionOutcome so the two
 * endpoints always report the transaction + budget picture the same way.
 */
public class TransactionResponse {

    private final String transactionId;
    private final String status;
    private final boolean duplicate;
    private final double amount;
    private final String merchant;
    private final String paymentMethod;
    private final LocalDateTime transactionDate;
    private final boolean hasBudget;
    private final String budgetStatus;
    private final String budgetMessage;
    private final double monthlySpending;
    private final double budgetAmount;
    private final double remainingBudget;

    private TransactionResponse(String transactionId, String status, boolean duplicate, double amount,
                                 String merchant, String paymentMethod, LocalDateTime transactionDate,
                                 boolean hasBudget, String budgetStatus, String budgetMessage,
                                 double monthlySpending, double budgetAmount, double remainingBudget) {
        this.transactionId = transactionId;
        this.status = status;
        this.duplicate = duplicate;
        this.amount = amount;
        this.merchant = merchant;
        this.paymentMethod = paymentMethod;
        this.transactionDate = transactionDate;
        this.hasBudget = hasBudget;
        this.budgetStatus = budgetStatus;
        this.budgetMessage = budgetMessage;
        this.monthlySpending = monthlySpending;
        this.budgetAmount = budgetAmount;
        this.remainingBudget = remainingBudget;
    }

    public static TransactionResponse from(TransactionOutcome outcome) {
        var txn = outcome.getTransaction();
        var budget = outcome.getBudgetCheck();
        return new TransactionResponse(
                txn.getExternalTransactionId(),
                txn.getStatus().name(),
                outcome.isDuplicate(),
                txn.getAmount(),
                txn.getMerchant(),
                txn.getPaymentMethod(),
                txn.getTransactionDate(),
                budget.isHasBudget(),
                budget.getStatus(),
                budget.getMessage(),
                budget.getCombinedTotal(),
                budget.getBudgetAmount(),
                budget.getRemaining()
        );
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getStatus() {
        return status;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public double getAmount() {
        return amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public boolean isHasBudget() {
        return hasBudget;
    }

    public String getBudgetStatus() {
        return budgetStatus;
    }

    public String getBudgetMessage() {
        return budgetMessage;
    }

    public double getMonthlySpending() {
        return monthlySpending;
    }

    public double getBudgetAmount() {
        return budgetAmount;
    }

    public double getRemainingBudget() {
        return remainingBudget;
    }
}
