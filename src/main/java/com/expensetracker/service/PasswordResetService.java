package com.expensetracker.service;

public interface PasswordResetService {

    /**
     * Generates and emails an OTP for the given email address, if an account
     * exists for it. Always returns normally (never reveals whether the email
     * is registered) to avoid leaking account existence to an attacker.
     */
    void requestOtp(String email);

    /**
     * Validates the OTP and, if valid, updates the user's password.
     *
     * @throws IllegalArgumentException if the email/OTP combination is invalid,
     *         expired, or already used
     */
    void resetPassword(String email, String otp, String newPassword);

}
