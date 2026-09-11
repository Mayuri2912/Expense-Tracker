package com.expensetracker.dto;

import java.time.LocalDate;

/**
 * Shown in the review inbox when an incoming online transaction looks like
 * something the user already entered by hand as an {@link
 * com.expensetracker.entity.Expense}. It is only a warning - the user can
 * still accept the transaction if it really is a separate spend.
 */
public class DuplicateWarning {

    private final Long expenseId;
    private final String expenseTitle;
    private final LocalDate expenseDate;
    private final double expenseAmount;
    private final String reason;

    public DuplicateWarning(Long expenseId, String expenseTitle, LocalDate expenseDate,
                             double expenseAmount, String reason) {
        this.expenseId = expenseId;
        this.expenseTitle = expenseTitle;
        this.expenseDate = expenseDate;
        this.expenseAmount = expenseAmount;
        this.reason = reason;
    }

    public Long getExpenseId() {
        return expenseId;
    }

    public String getExpenseTitle() {
        return expenseTitle;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public double getExpenseAmount() {
        return expenseAmount;
    }

    public String getReason() {
        return reason;
    }
}
