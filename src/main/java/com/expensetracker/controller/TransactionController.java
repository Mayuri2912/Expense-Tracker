package com.expensetracker.controller;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.expensetracker.dto.TransactionOutcome;
import com.expensetracker.dto.TransactionResponse;
import com.expensetracker.dto.TransactionTestRequest;
import com.expensetracker.dto.TransactionWebhookRequest;
import com.expensetracker.entity.User;
import com.expensetracker.service.TransactionService;

/**
 * Test/sandbox endpoints standing in for a real online-transaction provider.
 * A production integration would replace {@code /webhook}'s caller (a real
 * payment provider posting here instead of our own test script) - the
 * request/response shape and everything downstream of TransactionService
 * would not need to change.
 */
@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    // Lets the test endpoint be switched off outside a demo/dev environment
    // without touching code - see application.properties.
    @Value("${app.transactions.test-endpoint-enabled:true}")
    private boolean testEndpointEnabled;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ---------------- WEBHOOK (simulated payment provider) ----------------

    @PostMapping("/webhook")
    public ResponseEntity<TransactionResponse> receiveWebhook(@RequestBody TransactionWebhookRequest request) {
        TransactionOutcome outcome = transactionService.recordWebhookTransaction(request);
        return respond(outcome);
    }

    // ---------------- MANUAL TEST TRANSACTION ----------------

    @PostMapping("/test")
    public ResponseEntity<TransactionResponse> submitTestTransaction(@RequestBody TransactionTestRequest request,
                                                                       HttpSession session) {
        if (!testEndpointEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The test transaction endpoint is disabled");
        }
        User user = requireUser(session);
        TransactionOutcome outcome = transactionService.recordTestTransaction(user, request);
        return respond(outcome);
    }

    private ResponseEntity<TransactionResponse> respond(TransactionOutcome outcome) {
        // A duplicate delivery is still a success from the caller's point of
        // view (their transaction is recorded exactly once) - 200 rather
        // than 201, so a webhook retrying a delivery doesn't see an error.
        HttpStatus status = outcome.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransactionResponse.from(outcome));
    }

    private User requireUser(HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return user;
    }
}
