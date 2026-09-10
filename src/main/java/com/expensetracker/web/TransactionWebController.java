package com.expensetracker.web;

import java.time.LocalDateTime;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.dto.TransactionOutcome;
import com.expensetracker.dto.TransactionTestRequest;
import com.expensetracker.entity.User;
import com.expensetracker.service.TransactionService;

/**
 * The /transactions page: the user's online-transaction history, plus (for
 * this college-project demo, where no real payment provider is connected) a
 * simple form that submits a test transaction through the exact same
 * TransactionService.recordTestTransaction path used by
 * POST /api/transactions/test.
 */
@Controller
public class TransactionWebController {

    private static final java.util.List<String> PAYMENT_METHODS =
            java.util.List.of("UPI", "Card", "Net Banking", "Wallet");

    private final TransactionService transactionService;

    @Value("${app.transactions.test-endpoint-enabled:true}")
    private boolean testEndpointEnabled;

    public TransactionWebController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/transactions")
    public String transactions(Model model, HttpSession session) {
        User user = CurrentUser.from(session);

        model.addAttribute("transactions", transactionService.getAllTransactionsForUser(user));
        model.addAttribute("paymentMethods", PAYMENT_METHODS);
        model.addAttribute("testEndpointEnabled", testEndpointEnabled);
        return "transactions";
    }

    @PostMapping("/transactions/test-submit")
    public String submitTestTransaction(@RequestParam(required = false) String transactionId,
                                         @RequestParam Double amount,
                                         @RequestParam(required = false) String merchant,
                                         @RequestParam String paymentMethod,
                                         HttpSession session,
                                         RedirectAttributes redirectAttributes) {

        if (!testEndpointEnabled) {
            redirectAttributes.addFlashAttribute("error", "The test transaction feature is currently disabled");
            return "redirect:/transactions";
        }

        User user = CurrentUser.from(session);

        TransactionTestRequest request = new TransactionTestRequest();
        request.setTransactionId(transactionId);
        request.setAmount(amount);
        request.setMerchant(merchant);
        request.setPaymentMethod(paymentMethod);
        request.setTransactionDate(LocalDateTime.now());

        try {
            TransactionOutcome outcome = transactionService.recordTestTransaction(user, request);
            if (outcome.isDuplicate()) {
                redirectAttributes.addFlashAttribute("error",
                        "A transaction with that ID was already recorded - nothing new was added.");
            } else if (outcome.getBudgetCheck().getMessage() != null
                    && "danger".equals(outcome.getBudgetCheck().getStatus())) {
                redirectAttributes.addFlashAttribute("error", "Test transaction added. "
                        + outcome.getBudgetCheck().getMessage());
            } else {
                redirectAttributes.addFlashAttribute("success", "Test transaction added successfully.");
            }
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getReason());
        }

        return "redirect:/transactions";
    }
}
