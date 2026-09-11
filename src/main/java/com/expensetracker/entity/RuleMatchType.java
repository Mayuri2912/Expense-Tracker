package com.expensetracker.entity;

/**
 * How a {@link CategoryRule}'s {@code pattern} is compared against an incoming
 * transaction's merchant/description text (both lower-cased first).
 *
 * <ul>
 *   <li>{@link #CONTAINS} - the text contains the pattern as a substring
 *       (e.g. pattern {@code "swiggy"} matches {@code "UPI/SWIGGY LTD/..."}).
 *       This is the common case for bank-statement narrations.</li>
 *   <li>{@link #EQUALS} - the text equals the pattern exactly. Useful for a
 *       clean merchant name coming from a webhook.</li>
 * </ul>
 *
 * Kept deliberately small: no regex, so a user-created rule can never be a
 * performance or ReDoS hazard, and the matching stays easy to explain in the
 * "why was this categorized?" reason string.
 */
public enum RuleMatchType {
    CONTAINS,
    EQUALS
}
