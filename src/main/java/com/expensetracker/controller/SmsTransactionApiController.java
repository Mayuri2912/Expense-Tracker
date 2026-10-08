package com.expensetracker.controller;

import com.expensetracker.service.SmsAutoLogService;
import com.expensetracker.service.SmsParserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
public class SmsTransactionApiController {

    @Autowired
    private SmsAutoLogService smsAutoLogService;

    @Autowired
    private SmsParserService smsParserService;

    @PostMapping("/auto-log")
    public ResponseEntity<?> autoLogTransaction(
            @RequestHeader(value = "X-API-KEY", required = false) String apiKey,
            @RequestBody Map<String, String> payload) {

        String sender = payload.get("sender");
        String rawMessage = payload.get("rawMessage");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = payload.get("apiKey"); // Fallback
        }

        Map<String, Object> result = smsAutoLogService.processAndLogSms(apiKey, sender, rawMessage);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/parse-preview")
    public ResponseEntity<?> parsePreview(@RequestBody Map<String, String> payload) {
        String rawMessage = payload.get("rawMessage");
        Map<String, Object> result = smsParserService.parseSms(rawMessage);
        return ResponseEntity.ok(result);
    }
}