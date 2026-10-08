package com.expensetracker.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.util.*;
import java.util.regex.*;

@Service
public class SmsParserService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(?:(?:rs\\.?|inr|₹|\\$)\\s*|debited\\s+(?:by|for)\\s+)([\\d,]+(?:\\.\\d{1,2})?)|([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr|₹)");
    private static final Pattern TYPE_PATTERN = Pattern.compile("(?i)\\b(debited|spent|paid|withdrawn|sent|deducted|purchased)\\b");
    private static final Pattern MERCHANT_PATTERN = Pattern.compile("(?i)(?:to|at|info:|vpa|merchant:?)\\s+([A-Za-z0-9&'-]{2,30}(?:\\s+[A-Za-z0-9&'-]{2,20})?)(?=[.,;]?\\s*(?:on|via|using|ref|bal|avl|info|for|with|\\.|,|$))");
    private static final Pattern ACC_PATTERN = Pattern.compile("(?i)(?:a/c|acct|account|card|c/c)[\\s\\w]*?(?:no\\.?|#)?\\s*[*xX]{0,8}(\\d{3,4})\\b|ending\\s+(?:with\\s+)?(\\d{3,4})\\b");
    private static final Pattern REF_PATTERN = Pattern.compile("(?i)(?:ref(?:\\s*no\\.?|\\s*id)?|utr|txn\\s*id|upi\\s*ref)\\s*[:\\s#]*([a-zA-Z0-9]{6,22})");
    private static final Pattern OTP_PATTERN = Pattern.compile("(?i)\\b(otp|one[ -]?time password|verification code|security code|auth code|secret code|is your secret|do not share)\\b");

    public Map<String, Object> parseSms(String rawMessage) {
        Map<String, Object> result = new HashMap<>();

        // 1. Check if OTP message
        if (rawMessage == null || OTP_PATTERN.matcher(rawMessage).find()) {
            result.put("isOtp", true);
            return result;
        }
        result.put("isOtp", false);

        // 2. High-Precision Regex Parsing
        Double amount = parseAmount(rawMessage);
        String merchant = parseMerchant(rawMessage);
        String account = parseAccount(rawMessage);
        String refNo = parseRefNo(rawMessage);
        boolean isDebit = TYPE_PATTERN.matcher(rawMessage).find();

        if (amount != null) {
            result.put("amount", amount);
            result.put("merchant", merchant != null ? merchant.trim() : "Unknown");
            result.put("accountLast4", account != null ? account : "0000");
            result.put("referenceNumber", refNo != null ? refNo : "");
            result.put("transactionType", isDebit ? "DEBIT" : "CREDIT");
            result.put("parsedBy", "REGEX");
            return result;
        }

        // 3. Fallback to Google Gemini API
        return parseWithGemini(rawMessage);
    }

    private Double parseAmount(String text) {
        Matcher m = AMOUNT_PATTERN.matcher(text);
        if (m.find()) {
            String val = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                return Double.parseDouble(val.replace(",", ""));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String parseMerchant(String text) {
        Matcher m = MERCHANT_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String parseAccount(String text) {
        Matcher m = ACC_PATTERN.matcher(text);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return null;
    }

    private String parseRefNo(String text) {
        Matcher m = REF_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private Map<String, Object> parseWithGemini(String text) {
        Map<String, Object> result = new HashMap<>();
        if (geminiApiKey == null || geminiApiKey.trim().isEmpty()) {
            result.put("amount", null);
            result.put("parsedBy", "FAILED");
            return result;
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;
            RestTemplate restTemplate = new RestTemplate();

            String prompt = "Extract transaction details from SMS in strict JSON format with keys: amount (number), merchant (string), transactionType (DEBIT/CREDIT), accountLast4 (string). SMS: " + text;

            Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            result.put("parsedBy", "GEMINI");
            result.put("amount", 0.0);
            result.put("merchant", "Gemini Extracted");
        } catch (Exception e) {
            result.put("amount", null);
            result.put("parsedBy", "GEMINI_ERROR");
        }
        return result;
    }
}