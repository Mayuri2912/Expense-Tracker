package com.expensetracker.exception;

/**
 * Thrown when a requested Expense, Category, Budget, or User cannot be
 * found, or does not belong to the currently logged-in user.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
