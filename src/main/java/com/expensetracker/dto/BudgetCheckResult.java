package com.expensetracker.dto;

/**
 * Result of comparing a user's combined spending (existing Expense records
 * plus SUCCESS online transactions) for one month against their Budget for
 * that month. Built once in TransactionServiceImpl.evaluateBudget and reused
 * both right after a transaction is processed and when the dashboard simply
 * wants to display the current state - so the two never drift apart.
 *
 * "status" uses the same three labels as PageController's existing
 * dashboard budget banner ("success" / "warning" / "danger"), plus "none"
 * when no budget is set for the month, so the two pieces of UI stay
 * consistent with each other.
 */
public class BudgetCheckResult {

    private final boolean hasBudget;
    private final double budgetAmount;
    private final double monthlyExpenseTotal;
    private final double monthlyTransactionTotal;
    private final double combinedTotal;
    private final double remaining;
    private final double percentUsed;
    private final String status;
    private final String message;

    public BudgetCheckResult(boolean hasBudget, double budgetAmount, double monthlyExpenseTotal,
                              double monthlyTransactionTotal, double combinedTotal, double remaining,
                              double percentUsed, String status, String message) {
        this.hasBudget = hasBudget;
        this.budgetAmount = budgetAmount;
        this.monthlyExpenseTotal = monthlyExpenseTotal;
        this.monthlyTransactionTotal = monthlyTransactionTotal;
        this.combinedTotal = combinedTotal;
        this.remaining = remaining;
        this.percentUsed = percentUsed;
        this.status = status;
        this.message = message;
    }

    public boolean isHasBudget() {
        return hasBudget;
    }

    public double getBudgetAmount() {
        return budgetAmount;
    }

    public double getMonthlyExpenseTotal() {
        return monthlyExpenseTotal;
    }

    public double getMonthlyTransactionTotal() {
        return monthlyTransactionTotal;
    }

    public double getCombinedTotal() {
        return combinedTotal;
    }

    public double getRemaining() {
        return remaining;
    }

    public double getPercentUsed() {
        return percentUsed;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
