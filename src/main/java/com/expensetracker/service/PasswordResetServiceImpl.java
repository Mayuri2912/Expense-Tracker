package com.expensetracker.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.expensetracker.entity.PasswordResetOtp;
import com.expensetracker.entity.User;
import com.expensetracker.repository.PasswordResetOtpRepository;
import com.expensetracker.repository.UserRepository;

@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Logger log =
            LoggerFactory.getLogger(PasswordResetServiceImpl.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.otp.expiry-minutes:10}")
    private int otpExpiryMinutes;

    public PasswordResetServiceImpl(
            UserRepository userRepository,
            PasswordResetOtpRepository otpRepository,
            PasswordEncoder passwordEncoder,
            EmailService emailService) {

        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Override
    public void requestOtp(String email) {

        Optional<User> userOpt =
                userRepository.findByEmail(email == null ? "" : email.trim());

        // Deliberately do nothing if the email isn't registered.
        if (userOpt.isEmpty()) {
            return;
        }

        User user = userOpt.get();

        String otp = generateOtp();

        PasswordResetOtp resetOtp =
                new PasswordResetOtp();

        resetOtp.setUser(user);
        resetOtp.setOtp(otp);
        resetOtp.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(otpExpiryMinutes)
        );
        resetOtp.setUsed(false);

        otpRepository.save(resetOtp);

        String subject =
                "Expense Tracker - Password Reset OTP";

        String body =
                "Hi " + user.getUsername() + ",\n\n"
                + "Your one-time password (OTP) to reset your "
                + "Expense Tracker password is:\n\n"
                + "    " + otp + "\n\n"
                + "This OTP expires in "
                + otpExpiryMinutes
                + " minutes. "
                + "If you did not request a password reset, "
                + "you can safely ignore this email.\n\n"
                + "- Expense Tracker";

        boolean sent =
                emailService.sendPlainTextEmail(
                        user.getEmail(),
                        subject,
                        body
                );

        if (!sent) {
            otpRepository.delete(resetOtp);
            log.warn("Password reset OTP email delivery failed for user id {}", user.getId());
            return;
        }

        log.info("Password reset OTP email sent for user id {}", user.getId());
    }

    @Override
    public void resetPassword(
            String email,
            String otp,
            String newPassword) {

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid email or OTP"
                                ));

        PasswordResetOtp resetOtp =
                otpRepository
                        .findTopByUserAndUsedFalseOrderByCreatedAtDesc(
                                user
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid or expired OTP"
                                ));

        if (!resetOtp.getOtp().equals(otp)
                || resetOtp.isExpired()) {

            throw new IllegalArgumentException(
                    "Invalid or expired OTP"
            );
        }

        user.setPassword(
                passwordEncoder.encode(newPassword)
        );

        userRepository.save(user);

        resetOtp.setUsed(true);
        otpRepository.save(resetOtp);
    }

    private String generateOtp() {

        int code =
                RANDOM.nextInt(1_000_000);

        return String.format("%06d", code);
    }
}
