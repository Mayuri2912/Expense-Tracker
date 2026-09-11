package com.expensetracker.entity;

/**
 * Where an ingested {@link Transaction} sits in the user's review workflow.
 *
 * <p>Every transaction starts {@link #NEEDS_REVIEW}. It only leaves that
 * state through an explicit action on the /transactions review inbox:
 * <ul>
 *   <li>{@link #ACCEPTED} - the user confirmed it; a real {@link Expense} was
 *       created from it (linked via {@code Transaction.expense}) and now flows
 *       through every existing dashboard/chart/budget/export unchanged.</li>
 *   <li>{@link #IGNORED} - the user dismissed it (already logged by hand, not
 *       their spending, a refund, etc.); it is never counted anywhere.</li>
 * </ul>
 *
 * <p>This is a different axis from {@link TransactionStatus} (the payment
 * outcome reported by the provider - SUCCESS/FAILED/PENDING). A transaction
 * can be {@code SUCCESS} and still {@code NEEDS_REVIEW}.
 */
public enum ReviewStatus {
    NEEDS_REVIEW,
    ACCEPTED,
    IGNORED
}
