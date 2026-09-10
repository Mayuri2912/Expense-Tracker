package com.expensetracker.dto;

import java.time.LocalDateTime;

/**
 * Payload accepted by the test/sandbox transaction webhook
 * (POST /api/transactions/webhook). Deliberately a plain DTO rather than the
 * Transaction entity itself, since the caller identifies the user by email
 * (a webhook has no session) and the entity has no such field.
 *
 * This stands in for what a real payment provider's webhook body would look
 * like. Swapping in an actual provider later means adding a new
 * TransactionService implementation (or a translation layer in front of this
 * one) - the rest of the application does not need to change.
 */
public class TransactionWebhookRequest {

    private String transactionId;
    private String userEmail;
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

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
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
