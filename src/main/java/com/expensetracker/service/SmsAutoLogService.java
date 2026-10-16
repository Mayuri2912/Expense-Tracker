package com.expensetracker.service;

import com.expensetracker.entity.Expense;
import com.expensetracker.entity.SmsTransaction;
import com.expensetracker.entity.User;
import com.expensetracker.repository.ExpenseRepository;
import com.expensetracker.repository.CategoryRepository;
import com.expensetracker.entity.Category;
import com.expensetracker.repository.SmsTransactionRepository;
import com.expensetracker.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class SmsAutoLogService {

    @Autowired
    private SmsTransactionRepository smsTransactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SmsParserService smsParserService;

    @Autowired
    private AutoCategorizationService autoCategorizationService;

    public Map<String, Object> processAndLogSms(
            String apiKey,
            String sender,
            String rawMessage) {

        Map<String, Object> response = new HashMap<>();

        // 1. Validate User API Key
        User user = userRepository.findByApiKey(apiKey).orElse(null);

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key");
        }

        // 2. Parse SMS
        Map<String, Object> parsed = smsParserService.parseSms(rawMessage);

        if ((Boolean) parsed.getOrDefault("isOtp", false)) {
            response.put("status", "IGNORED");
            response.put("message", "OTP Message Discarded");
            return response;
        }

        Double amount = (Double) parsed.get("amount");

        if (amount == null || amount <= 0) {
            response.put("status", "FAILED");
            response.put("message", "Could not parse transaction amount");
            return response;
        }

        if (!"DEBIT".equals(parsed.get("transactionType"))) {
            response.put("status", "IGNORED");
            response.put("message", "Only debit transactions are recorded");
            return response;
        }

        // 3. Check Deduplication Hash
        String refNo =
                (String) parsed.getOrDefault("referenceNumber", "");

        String hashInput =
                user.getId() + (refNo.isEmpty() ? rawMessage : refNo);

        String smsHash = generateSHA256(hashInput);

        if (smsTransactionRepository
                .existsByUserIdAndSmsHash(user.getId(), smsHash)) {

            response.put("status", "DUPLICATE");
            response.put("message", "Transaction already logged");
            return response;
        }

        // 4. Categorize Merchant
        String merchant = (String) parsed.get("merchant");

        String category =
                autoCategorizationService.categorizeMerchant(merchant);

        Category expenseCategory = categoryRepository
                .findByNameIgnoreCaseAndUser(category, user)
                .orElseGet(() -> {
                    Category created = new Category();
                    created.setName(category);
                    created.setUser(user);
                    return categoryRepository.save(created);
                });

        // 5. Create Expense
        Expense expense = new Expense();

        expense.setUser(user);
        expense.setTitle(
                merchant == null || merchant.isBlank()
                        ? "SMS Transaction"
                        : merchant
        );
        expense.setAmount(amount);
        expense.setCategory(category);
        expense.setCategoryId(expenseCategory.getId());
        expense.setDate(LocalDate.now());
        expense.setSource("SMS");

        Expense savedExpense = expenseRepository.save(expense);

        // 6. Save SMS Audit Record
        SmsTransaction tx = new SmsTransaction();

        tx.setUserId(user.getId());
        tx.setSmsHash(smsHash);
        tx.setSender(sender);
        tx.setAmount(amount);
        tx.setMerchant(merchant);
        tx.setTransactionType(
                (String) parsed.get("transactionType")
        );
        tx.setAccountLast4(
                (String) parsed.get("accountLast4")
        );
        tx.setReferenceNumber(refNo);
        tx.setRawMessage(rawMessage);
        tx.setStatus("SUCCESS");
        tx.setExpenseId(savedExpense.getId());

        smsTransactionRepository.save(tx);

        // 7. Response
        response.put("status", "SUCCESS");
        response.put("amount", amount);
        response.put("merchant", merchant);
        response.put("category", category);

        return response;
    }

    private String generateSHA256(String input) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            input.getBytes(StandardCharsets.UTF_8)
                    );

            StringBuilder hexString =
                    new StringBuilder();

            for (byte b : hash) {

                String hex =
                        Integer.toHexString(0xff & b);

                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.toString();

        } catch (Exception e) {
            throw new RuntimeException(
                    "Unable to generate SHA-256 hash",
                    e
            );
        }
    }
}
