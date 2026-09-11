package com.expensetracker.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.expensetracker.dto.AcceptTransactionRequest;
import com.expensetracker.dto.StatementImportResult;
import com.expensetracker.dto.TransactionOutcome;
import com.expensetracker.dto.TransactionTestRequest;
import com.expensetracker.entity.ReviewStatus;
import com.expensetracker.entity.User;
import com.expensetracker.service.CategoryService;
import com.expensetracker.service.TransactionService;

/**
 * The Transaction Hub: /transactions shows the review inbox (imported
 * transactions awaiting Accept/Edit/Ignore), detected recurring series, and a
 * full history. For this college-project demo, where no real payment provider
 * is connected, the page also offers a CSV/Excel statement import and a
 * simple "simulate a test transaction" form that goes through the exact same
 * TransactionService path a real webhook would use.
 */
@Controller
public class TransactionWebController {

    private static final List<String> PAYMENT_METHODS =
            List.of("UPI", "Card", "Net Banking", "Wallet", "Cash");

    private final TransactionService transactionService;
    private final CategoryService categoryService;

    @Value("${app.transactions.test-endpoint-enabled:true}")
    private boolean testEndpointEnabled;

    public TransactionWebController(TransactionService transactionService, CategoryService categoryService) {
        this.transactionService = transactionService;
        this.categoryService = categoryService;
    }

    @GetMapping("/transactions")
    public String transactions(Model model, HttpSession session) {
        User user = CurrentUser.from(session);

        model.addAttribute("reviewInbox", transactionService.getReviewInbox(user));
        model.addAttribute("acceptedTransactions", transactionService.getTransactionsByReviewStatus(user, ReviewStatus.ACCEPTED));
        model.addAttribute("ignoredTransactions", transactionService.getTransactionsByReviewStatus(user, ReviewStatus.IGNORED));
        model.addAttribute("recurringGroups", transactionService.getRecurringGroups(user));
        model.addAttribute("categories", categoryService.getAllCategoriesForUser(user));
        model.addAttribute("paymentMethods", PAYMENT_METHODS);
        model.addAttribute("testEndpointEnabled", testEndpointEnabled);
        return "transactions";
    }

    // ---------------- STATEMENT IMPORT ----------------

    @PostMapping("/transactions/import")
    public String importStatement(@RequestParam("file") MultipartFile file,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        User user = CurrentUser.from(session);
        try {
            StatementImportResult result = transactionService.importStatement(user, file);
            redirectAttributes.addFlashAttribute("success", result.summary());
            if (!result.getMessages().isEmpty()) {
                redirectAttributes.addFlashAttribute("importWarnings", result.getMessages());
            }
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/transactions";
    }

    // ---------------- REVIEW WORKFLOW: ACCEPT / IGNORE / UN-IGNORE ----------------

    @PostMapping("/transactions/{id}/accept")
    public String accept(@PathVariable Long id,
                          @RequestParam Long categoryId,
                          @RequestParam(required = false) String title,
                          @RequestParam(required = false) Double amount,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expenseDate,
                          @RequestParam(required = false) String paymentMethod,
                          @RequestParam(required = false, defaultValue = "false") boolean rememberRule,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        User user = CurrentUser.from(session);

        AcceptTransactionRequest request = new AcceptTransactionRequest();
        request.setCategoryId(categoryId);
        request.setTitle(title);
        request.setAmount(amount);
        request.setExpenseDate(expenseDate);
        request.setPaymentMethod(paymentMethod);
        request.setRememberRule(rememberRule);

        try {
            transactionService.acceptTransaction(id, user, request);
            redirectAttributes.addFlashAttribute("success", "Transaction accepted and added to your expenses.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/transactions";
    }

    @PostMapping("/transactions/{id}/ignore")
    public String ignore(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = CurrentUser.from(session);
        try {
            transactionService.ignoreTransaction(id, user);
            redirectAttributes.addFlashAttribute("success", "Transaction ignored.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/transactions";
    }

    @PostMapping("/transactions/{id}/unignore")
    public String unignore(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        User user = CurrentUser.from(session);
        try {
            transactionService.moveBackToReview(id, user);
            redirectAttributes.addFlashAttribute("success", "Moved back to the review inbox.");
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getReason());
        }
        return "redirect:/transactions";
    }

    // ---------------- MANUAL TEST TRANSACTION (sandbox only) ----------------

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
            } else {
                redirectAttributes.addFlashAttribute("success",
                        "Test transaction added - it's in your review inbox below.");
            }
        } catch (ResponseStatusException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getReason());
        }

        return "redirect:/transactions";
    }
}
