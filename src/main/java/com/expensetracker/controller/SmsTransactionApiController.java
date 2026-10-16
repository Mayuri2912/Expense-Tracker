package com.expensetracker.controller;

import com.expensetracker.entity.SmsTransaction;
import com.expensetracker.entity.User;
import com.expensetracker.repository.SmsTransactionRepository;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.SmsAutoLogService;
import com.expensetracker.service.SmsParserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/transactions")
public class SmsTransactionApiController {
    private final SmsAutoLogService smsAutoLogService;
    private final SmsParserService smsParserService;
    private final SmsTransactionRepository smsTransactionRepository;
    private final UserRepository userRepository;

    public SmsTransactionApiController(SmsAutoLogService smsAutoLogService, SmsParserService smsParserService,
                                       SmsTransactionRepository smsTransactionRepository, UserRepository userRepository) {
        this.smsAutoLogService = smsAutoLogService;
        this.smsParserService = smsParserService;
        this.smsTransactionRepository = smsTransactionRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("/auto-log")
    public ResponseEntity<?> autoLogTransaction(@RequestHeader(value = "X-API-KEY", required = false) String apiKey,
                                                @RequestBody Map<String, String> payload, HttpSession session) {
        if (apiKey == null || apiKey.isBlank()) {
            User sessionUser = session == null ? null : (User) session.getAttribute("loggedInUser");
            if (sessionUser == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
            apiKey = sessionUser.getApiKey();
        }
        Map<String, Object> result = smsAutoLogService.processAndLogSms(apiKey, payload.get("sender"), payload.get("rawMessage"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/parse-preview")
    public ResponseEntity<?> parsePreview(@RequestHeader(value = "X-API-KEY", required = false) String apiKey,
                                          @RequestBody Map<String, String> payload, HttpSession session) {
        requireUser(apiKey, session);
        return ResponseEntity.ok(smsParserService.parseSms(payload.get("rawMessage")));
    }

    @GetMapping("/history")
    public List<SmsTransaction> history(@RequestHeader(value = "X-API-KEY", required = false) String apiKey,
                                        HttpSession session) {
        User user = requireUser(apiKey, session);
        return smsTransactionRepository.findByUserIdOrderByReceivedAtDesc(user.getId());
    }

    private User requireUser(String apiKey, HttpSession session) {
        User sessionUser = session == null ? null : (User) session.getAttribute("loggedInUser");
        if (sessionUser != null) return sessionUser;
        User apiUser = apiKey == null || apiKey.isBlank() ? null : userRepository.findByApiKey(apiKey).orElse(null);
        if (apiUser == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        return apiUser;
    }
}
