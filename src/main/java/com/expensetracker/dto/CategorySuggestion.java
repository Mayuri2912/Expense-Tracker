package com.expensetracker.dto;

import com.expensetracker.entity.Category;

/**
 * The categorization engine's answer for one transaction: which category to
 * suggest, whether it could be tied to one of the user's real categories, a
 * plain-English reason, and a rough confidence label.
 *
 * <p>This is only ever a <em>suggestion</em> shown in the review inbox - it is
 * never used to create an Expense without the user confirming it.
 */
public class CategorySuggestion {

    /** Confidence buckets, kept coarse on purpose so they are easy to justify. */
    public enum Confidence { HIGH, MEDIUM, LOW, NONE }

    private final boolean matched;
    private final String categoryName;      // null only when matched == false
    private final Category category;         // the user's own category, or null if they have none by that name
    private final String reason;
    private final Confidence confidence;

    private CategorySuggestion(boolean matched, String categoryName, Category category,
                                String reason, Confidence confidence) {
        this.matched = matched;
        this.categoryName = categoryName;
        this.category = category;
        this.reason = reason;
        this.confidence = confidence;
    }

    public static CategorySuggestion none() {
        return new CategorySuggestion(false, null, null,
                "No rule matched this merchant - pick a category to file it under.", Confidence.NONE);
    }

    public static CategorySuggestion of(String categoryName, Category category, String reason, Confidence confidence) {
        return new CategorySuggestion(true, categoryName, category, reason, confidence);
    }

    public boolean isMatched() {
        return matched;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public Category getCategory() {
        return category;
    }

    /** True when the suggestion is tied to a real Category row the user owns. */
    public boolean isResolved() {
        return category != null;
    }

    public String getReason() {
        return reason;
    }

    public Confidence getConfidence() {
        return confidence;
    }
}
