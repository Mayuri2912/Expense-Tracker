package com.expensetracker.dto;

import java.time.LocalDate;

/**
 * The user's decision when accepting a reviewed transaction into their
 * expenses. Every field except {@code categoryId} is optional - anything left
 * blank falls back to the transaction's own value.
 */
public class AcceptTransactionRequest {

    private Long categoryId;
    private String title;
    private Double amount;
    private LocalDate expenseDate;
    private String paymentMethod;
    /** If true, save "merchant -> this category" as a personal rule for next time. */
    private boolean rememberRule;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public LocalDate getExpenseDate() { return expenseDate; }
    public void setExpenseDate(LocalDate expenseDate) { this.expenseDate = expenseDate; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public boolean isRememberRule() { return rememberRule; }
    public void setRememberRule(boolean rememberRule) { this.rememberRule = rememberRule; }
}
