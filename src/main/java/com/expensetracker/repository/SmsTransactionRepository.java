package com.expensetracker.repository;

import com.expensetracker.entity.SmsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SmsTransactionRepository extends JpaRepository<SmsTransaction, Long> {
    boolean existsByUserIdAndSmsHash(Long userId, String smsHash);
    List<SmsTransaction> findByUserIdOrderByReceivedAtDesc(Long userId);
}