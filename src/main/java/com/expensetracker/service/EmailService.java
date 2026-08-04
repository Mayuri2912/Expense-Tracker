package com.expensetracker.service;

public interface EmailService {

    /**
     * Sends a plain-text email. Implementations must not throw on failure
     * (e.g. SMTP not configured) - they should log the problem instead, so a
     * mail outage never breaks the surrounding feature (OTP generation,
     * registration, etc. all still succeed even if the email itself fails).
     *
     * @return true if the email was sent successfully, false otherwise
     */
    boolean sendPlainTextEmail(String to, String subject, String body);

}
