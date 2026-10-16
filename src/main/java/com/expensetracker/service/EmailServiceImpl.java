package com.expensetracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final String mailPassword;

    public EmailServiceImpl(JavaMailSender mailSender,
                            @Value("${spring.mail.username:}") String mailUsername,
                            @Value("${spring.mail.password:}") String mailPassword) {
        this.mailSender = mailSender;
        this.mailUsername = mailUsername == null ? "" : mailUsername.trim();
        this.mailPassword = mailPassword == null ? "" : mailPassword.trim();
    }

    @Override
    public boolean isConfigured() {
        return !mailUsername.isBlank() && !mailPassword.isBlank();
    }

    @Override
    public boolean sendPlainTextEmail(String to, String subject, String body) {
        if (!isConfigured()) {
            log.error("Email delivery is not configured; set MAIL_USERNAME and MAIL_PASSWORD.");
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Unable to send email ({}): {}", ex.getClass().getSimpleName(), ex.getMessage());
            return false;
        }
    }
}
