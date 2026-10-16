package com.expensetracker.service;

public interface EmailService {

    /** Returns whether SMTP credentials are available for sending mail. */
    boolean isConfigured();

    /**
     * Sends a plain-text email and returns false if SMTP delivery fails.
     *
     * @return true if the email was sent successfully, false otherwise
     */
    boolean sendPlainTextEmail(String to, String subject, String body);

}
