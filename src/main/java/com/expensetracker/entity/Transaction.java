package com.expensetracker.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * An online/UPI-style transaction received from a transaction provider (a
 * real payment webhook in production, or the test/sandbox endpoint used for
 * this demo). This is intentionally kept separate from {@link Expense}:
 *
 * - An Expense always requires a user-chosen category (category_id is
 *   NOT NULL), which an incoming transaction never carries, so a Transaction
 *   is never auto-converted into an Expense.
 * - Keeping them separate also means a transaction that arrives for a
 *   purchase the user already logged by hand can never silently become a
 *   second, duplicate expense record.
 *
 * The optional {@link #expense} reference exists so a future "convert this
 * transaction into an Expense" action has somewhere to record the link once
 * the user picks a category - it is never set automatically.
 */
@Entity
@Table(name = "transactions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"external_transaction_id"}))
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The provider's own transaction/reference ID. Unique so the same
    // webhook delivery can never be recorded twice - see
    // TransactionServiceImpl for the full duplicate-protection flow.
    @NotBlank(message = "Transaction ID is required")
    @Column(name = "external_transaction_id", nullable = false, unique = true)
    private String externalTransactionId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Column(nullable = false)
    private Double amount;

    @NotNull(message = "Transaction date is required")
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @Column(name = "payment_method")
    private String paymentMethod;

    private String merchant;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Set only when this transaction is ACCEPTED in the review inbox and a
    // real Expense is created from it - never populated automatically.
    @OneToOne
    @JoinColumn(name = "expense_id")
    private Expense expense;

    // ---- Smart Transaction Review Hub fields ----
    // All nullable / defaulted, layered on top of the ingestion base. A
    // transaction starts NEEDS_REVIEW and only leaves that state through an
    // explicit user action on the /transactions review inbox.

    // columnDefinition forces a plain VARCHAR rather than a native MySQL ENUM
    // column. A MySQL ENUM/SET column that is NOT NULL with no explicit
    // DEFAULT silently defaults any existing row to its FIRST value in
    // alphabetical order when added via ALTER TABLE (ddl-auto=update on an
    // existing table) - here that would have been "ACCEPTED", not
    // "NEEDS_REVIEW". The explicit DEFAULT below is what actually makes
    // existing rows backfill correctly.
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20,
            columnDefinition = "VARCHAR(20) DEFAULT 'NEEDS_REVIEW'")
    private ReviewStatus reviewStatus = ReviewStatus.NEEDS_REVIEW;

    // The categorization engine's best guess, resolved to one of THIS user's
    // own categories. Null when no rule matched (the review screen then just
    // asks the user to pick one). Never used to auto-create an Expense.
    @ManyToOne
    @JoinColumn(name = "suggested_category_id")
    private Category suggestedCategory;

    // Human-readable explanation shown next to the suggestion, e.g.
    // "Merchant contains 'swiggy' -> Food (high confidence)".
    @Column(name = "suggestion_reason")
    private String suggestionReason;

    // Set by RecurringDetectionService when this transaction looks like one
    // instalment of a repeating series (same merchant, ~same amount, roughly
    // monthly). recurringGroupKey ties the members of a series together.
    @Column(nullable = false)
    private boolean recurring = false;

    @Column(name = "recurring_group_key")
    private String recurringGroupKey;

    public Transaction() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.reviewStatus == null) {
            this.reviewStatus = ReviewStatus.NEEDS_REVIEW;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public void setExternalTransactionId(String externalTransactionId) {
        this.externalTransactionId = externalTransactionId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
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

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Expense getExpense() {
        return expense;
    }

    public void setExpense(Expense expense) {
        this.expense = expense;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Category getSuggestedCategory() {
        return suggestedCategory;
    }

    public void setSuggestedCategory(Category suggestedCategory) {
        this.suggestedCategory = suggestedCategory;
    }

    public String getSuggestionReason() {
        return suggestionReason;
    }

    public void setSuggestionReason(String suggestionReason) {
        this.suggestionReason = suggestionReason;
    }

    public boolean isRecurring() {
        return recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public String getRecurringGroupKey() {
        return recurringGroupKey;
    }

    public void setRecurringGroupKey(String recurringGroupKey) {
        this.recurringGroupKey = recurringGroupKey;
    }
}
