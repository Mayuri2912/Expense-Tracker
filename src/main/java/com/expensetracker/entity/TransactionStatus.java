package com.expensetracker.entity;

/**
 * Status of an incoming online/UPI transaction, as reported by the
 * transaction provider (or the test/sandbox endpoint standing in for one).
 * Only SUCCESS transactions are counted toward a user's monthly spending -
 * see TransactionServiceImpl.
 */
public enum TransactionStatus {
    SUCCESS,
    FAILED,
    PENDING
}
