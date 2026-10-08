package com.expensetracker.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sms_transactions")
public class SmsTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sms_hash", nullable = false, length = 64)
    private String smsHash;

    private String sender;
    private Double amount;

    @Column(name = "transaction_type")
    private String transactionType;

    private String merchant;

    @Column(name = "account_last4")
    private String accountLast4;

    @Column(name = "reference_number")
    private String referenceNumber;

    @Column(columnDefinition = "TEXT")
    private String rawMessage;

    private String status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "received_at", insertable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "expense_id")
    private Long expenseId;

    public SmsTransaction() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSmsHash() { return smsHash; }
    public void setSmsHash(String smsHash) { this.smsHash = smsHash; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }

    public String getAccountLast4() { return accountLast4; }
    public void setAccountLast4(String accountLast4) { this.accountLast4 = accountLast4; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public LocalDateTime getReceivedAt() { return receivedAt; }

    public Long getExpenseId() { return expenseId; }
    public void setExpenseId(Long expenseId) { this.expenseId = expenseId; }
}