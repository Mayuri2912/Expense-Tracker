package com.expensetracker.entity;

import java.time.LocalDate;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank(message = "Title is required")
    @Column(nullable = false)
    private String title;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Column(nullable = false)
    private Double amount;

    @Column(length = 100)
    private String category;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category categoryRecord;

    @Column(length = 255)
    private String description;

    @Column(name = "payment_method")
    private String paymentMethod;

    @NotNull(message = "Expense date is required")
    @Column(name = "expense_date", nullable = false)
    private LocalDate date;

    // The application previously wrote to `date`; the imported schema uses
    // `expense_date`. Keep both columns synchronized for existing databases.
    @Column(name = "date")
    private LocalDate legacyDate;

    @Column(name = "source", length = 30)
    private String source = "MANUAL";

    public Expense() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getCategory() {
        return category != null ? category : categoryRecord != null ? categoryRecord.getName() : null;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
        this.legacyDate = date;
    }

    public LocalDate getExpenseDate() {
        return date;
    }

    public void setExpenseDate(LocalDate expenseDate) {
        this.date = expenseDate;
        this.legacyDate = expenseDate;
    }

    @PostLoad
    private void populateExpenseDate() {
        if (date == null) {
            date = legacyDate;
        }
    }

    @PrePersist
    @PreUpdate
    private void synchronizeExpenseDateColumns() {
        if (date == null) {
            date = legacyDate;
        }
        legacyDate = date;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
