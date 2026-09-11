package com.expensetracker.dto;

import java.time.LocalDate;

/**
 * One spending row lifted out of a CSV/Excel bank statement, before it becomes
 * a {@link com.expensetracker.entity.Transaction}. Credits/deposits are
 * dropped during parsing, so every row here is money going out.
 */
public class ParsedStatementRow {

    private final int lineNumber;
    private final LocalDate date;
    private final double amount;
    private final String merchant;
    private final String description;
    private final String paymentMethod;
    private final String reference;

    public ParsedStatementRow(int lineNumber, LocalDate date, double amount, String merchant,
                               String description, String paymentMethod, String reference) {
        this.lineNumber = lineNumber;
        this.date = date;
        this.amount = amount;
        this.merchant = merchant;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.reference = reference;
    }

    public int getLineNumber() { return lineNumber; }
    public LocalDate getDate() { return date; }
    public double getAmount() { return amount; }
    public String getMerchant() { return merchant; }
    public String getDescription() { return description; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getReference() { return reference; }
}
