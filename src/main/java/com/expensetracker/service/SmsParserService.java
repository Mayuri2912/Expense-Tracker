package com.expensetracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.*;

@Service
public class SmsParserService {
    private static final Logger log = LoggerFactory.getLogger(SmsParserService.class);

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:(?:rs\\.?|inr|₹|\\$)\\s*|debited\\s+(?:by|for)\\s+)([\\d,]+(?:\\.\\d{1,2})?)|([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr|₹)"
    );
    private static final Pattern DEBIT_PATTERN = Pattern.compile("(?i)\\b(debited|spent|paid|withdrawn|sent|deducted|purchased)\\b");
    private static final Pattern CREDIT_PATTERN = Pattern.compile("(?i)\\b(credited|received|deposited|refund(?:ed)?)\\b");
    private static final Pattern MERCHANT_PATTERN = Pattern.compile(
            "(?i)(?:to|at|info:|vpa|merchant:?)\\s+([A-Za-z0-9&'-]{2,30}(?:\\s+[A-Za-z0-9&'-]{2,20})?)(?=[.,;]?\\s*(?:on|via|using|ref|bal|avl|info|for|with|\\.|,|$))"
    );
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
            "(?i)(?:a/c|acct|account|card|c/c)[\\s\\w]*?(?:no\\.?|#)?\\s*[\\*xX]{0,8}(\\d{3,4})\\b|ending\\s+(?:with\\s+)?(\\d{3,4})\\b"
    );
    private static final Pattern REF_PATTERN = Pattern.compile(
            "(?i)(?:ref(?:\\s*no\\.?|\\s*id)?|utr|txn\\s*id|upi\\s*ref)\\s*[:\\s#]*([a-zA-Z0-9]{6,22})"
    );
    private static final Pattern OTP_PATTERN = Pattern.compile(
            "(?i)\\b(otp|one[ -]?time password|verification code|security code|auth code|secret code|is your secret|do not share)\\b"
    );

    public Map<String, Object> parseSms(String rawMessage) {
        Map<String, Object> result = new HashMap<>();
        if (rawMessage == null || OTP_PATTERN.matcher(rawMessage).find()) {
            result.put("isOtp", true);
            return result;
        }
        result.put("isOtp", false);
        Matcher amountMatcher = AMOUNT_PATTERN.matcher(rawMessage);
        if (amountMatcher.find()) {
            String value = amountMatcher.group(1) != null ? amountMatcher.group(1) : amountMatcher.group(2);
            try {
                result.put("amount", Double.parseDouble(value.replace(",", "")));
            } catch (NumberFormatException ignored) {
                result.put("amount", null);
            }
            Matcher merchantMatcher = MERCHANT_PATTERN.matcher(rawMessage);
            Matcher accountMatcher = ACCOUNT_PATTERN.matcher(rawMessage);
            Matcher refMatcher = REF_PATTERN.matcher(rawMessage);
            result.put("merchant", merchantMatcher.find() ? merchantMatcher.group(1).trim() : "Unknown");
            result.put("accountLast4", accountMatcher.find() ? (accountMatcher.group(1) != null ? accountMatcher.group(1) : accountMatcher.group(2)) : null);
            result.put("referenceNumber", refMatcher.find() ? refMatcher.group(1) : "");
            result.put("transactionType", DEBIT_PATTERN.matcher(rawMessage).find() ? "DEBIT" : CREDIT_PATTERN.matcher(rawMessage).find() ? "CREDIT" : "UNKNOWN");
            result.put("parsedBy", "REGEX");
            return result;
        }
        return parseWithGemini(rawMessage);
    }

    private Map<String, Object> parseWithGemini(String text) {
        Map<String, Object> result = new HashMap<>();
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            result.put("amount", null);
            result.put("parsedBy", "FAILED");
            return result;
        }
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent";
            String prompt = "Extract transaction details from this SMS as JSON only, with amount (number or null), merchant (string), transactionType (DEBIT/CREDIT/UNKNOWN), accountLast4 (string or null), referenceNumber (string). Do not infer missing facts. SMS: " + text;
            Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", geminiApiKey);
            ResponseEntity<Map> response = new RestTemplate().postForEntity(url, new HttpEntity<>(body, headers), Map.class);
            Map<String, Object> parsed = extractGeminiResult(response.getBody());
            result.putAll(parsed);
            result.put("parsedBy", "GEMINI");
        } catch (Exception e) {
            log.warn("Gemini SMS parsing failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
            result.put("amount", null);
            result.put("parsedBy", "GEMINI_ERROR");
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractGeminiResult(Map response) throws Exception {
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        String json = String.valueOf(parts.get(0).get("text")).replaceAll("^```(?:json)?\\s*|\\s*```$", "").trim();
        Map<String, Object> fields = new ObjectMapper().readValue(json, Map.class);
        Map<String, Object> parsed = new HashMap<>();
        Object amount = fields.get("amount");
        parsed.put("amount", amount instanceof Number number ? number.doubleValue() : null);
        parsed.put("merchant", fields.getOrDefault("merchant", "Unknown"));
        parsed.put("transactionType", String.valueOf(fields.getOrDefault("transactionType", "UNKNOWN")).toUpperCase(Locale.ROOT));
        parsed.put("accountLast4", fields.get("accountLast4"));
        parsed.put("referenceNumber", fields.getOrDefault("referenceNumber", ""));
        return parsed;
    }
}
