package com.expensetracker.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.expensetracker.entity.PasswordResetOtp;
import com.expensetracker.entity.User;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByUserAndUsedFalseOrderByCreatedAtDesc(User user);

}
