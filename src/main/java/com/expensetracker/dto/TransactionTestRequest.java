package com.expensetracker.dto;

import java.time.LocalDateTime;

/**
 * Payload accepted by the manual test-transaction endpoint
 * (POST /api/transactions/test). No userEmail field on purpose - the
 * endpoint always uses the logged-in session user, never a caller-supplied
 * identity, so this class cannot be used to impersonate another user.
 *
 * transactionId, transactionDate and status are optional conveniences for
 * manual testing: a blank transactionId gets a generated one, a missing
 * transactionDate defaults to "now", and a missing status defaults to
 * SUCCESS. See TransactionServiceImpl.
 */
public class TransactionTestRequest {

    private String transactionId;
    private Double amount;
    private String paymentMethod;
    private String merchant;
    private LocalDateTime transactionDate;
    private String status;
    private String description;

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getMerchant() {
        return merchant;
    }

    public void setMerchant(String merchant) {
        this.merchant = merchant;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
