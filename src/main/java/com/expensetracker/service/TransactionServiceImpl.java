package com.expensetracker.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.expensetracker.dto.BudgetCheckResult;
import com.expensetracker.dto.TransactionOutcome;
import com.expensetracker.dto.TransactionTestRequest;
import com.expensetracker.dto.TransactionWebhookRequest;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionStatus;
import com.expensetracker.entity.User;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    // Injected directly rather than adding a new lookup method to
    // UserService: UserRepository.findByEmail already exists (the login flow
    // already uses it), so resolving the webhook's "userEmail" this way needs
    // no change to UserService/UserServiceImpl at all.
    private final UserRepository userRepository;

    private final ExpenseService expenseService;
    private final BudgetService budgetService;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                   UserRepository userRepository,
                                   ExpenseService expenseService,
                                   BudgetService budgetService) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.budgetService = budgetService;
    }

    @Override
    public TransactionOutcome recordWebhookTransaction(TransactionWebhookRequest request) {
        if (request.getUserEmail() == null || request.getUserEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userEmail is required");
        }
        User user = userRepository.findByEmail(request.getUserEmail().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No account found for the given userEmail"));

        return process(user,
                request.getTransactionId(),
                request.getAmount(),
                request.getPaymentMethod(),
                request.getMerchant(),
                request.getTransactionDate(),
                request.getStatus(),
                request.getDescription());
    }

    @Override
    public TransactionOutcome recordTestTransaction(User user, TransactionTestRequest request) {
        if (user == null) {
            // Should never happen - the controller resolves the session user
            // before calling this - but fail safely instead of a NullPointerException.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }

        String transactionId = (request.getTransactionId() == null || request.getTransactionId().isBlank())
                ? "TEST-" + UUID.randomUUID()
                : request.getTransactionId();

        LocalDateTime transactionDate = request.getTransactionDate() != null
                ? request.getTransactionDate()
                : LocalDateTime.now();

        String status = (request.getStatus() == null || request.getStatus().isBlank())
                ? TransactionStatus.SUCCESS.name()
                : request.getStatus();

        return process(user,
                transactionId,
                request.getAmount(),
                request.getPaymentMethod(),
                request.getMerchant(),
                transactionDate,
                status,
                request.getDescription());
    }

    /**
     * Shared validation + duplicate-protection + save + budget-check flow
     * used by both the webhook and the test endpoint, once each has already
     * resolved which User the transaction belongs to.
     */
    private TransactionOutcome process(User user, String transactionId, Double amount, String paymentMethod,
                                        String merchant, LocalDateTime transactionDate, String statusRaw,
                                        String description) {

        if (transactionId == null || transactionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionId is required");
        }
        if (amount == null || amount <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be a positive number");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "paymentMethod is required");
        }
        if (transactionDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "transactionDate is required");
        }
        TransactionStatus status = parseStatus(statusRaw);

        // ---- Duplicate protection ----
        // 1) Existence check first - the fast path for the common case of a
        //    webhook simply being redelivered with the same transactionId.
        Optional<Transaction> existing = transactionRepository.findByExternalTransactionId(transactionId);
        if (existing.isPresent()) {
            Transaction txn = existing.get();
            if (!txn.getUser().getId().equals(user.getId())) {
                // Someone else's transaction ID happens to collide - reject
                // rather than leaking or attaching to another user's data.
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "transactionId is already associated with a different user");
            }
            return buildOutcome(txn, true);
        }

        Transaction transaction = new Transaction();
        transaction.setExternalTransactionId(transactionId);
        transaction.setAmount(amount);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setMerchant(merchant);
        transaction.setTransactionDate(transactionDate);
        transaction.setStatus(status);
        transaction.setDescription(description);
        transaction.setUser(user);

        try {
            transaction = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException ex) {
            // 2) Safety net against the unique DB constraint, for a genuine
            //    race between two concurrent deliveries of the same
            //    transactionId. Whichever request committed first wins;
            //    report that one instead of failing this delivery.
            Transaction winner = transactionRepository.findByExternalTransactionId(transactionId)
                    .orElseThrow(() -> ex);
            return buildOutcome(winner, true);
        }

        return buildOutcome(transaction, false);
    }

    private TransactionStatus parseStatus(String statusRaw) {
        if (statusRaw == null || statusRaw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }
        try {
            return TransactionStatus.valueOf(statusRaw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported status '" + statusRaw + "' - expected SUCCESS, FAILED, or PENDING");
        }
    }

    private TransactionOutcome buildOutcome(Transaction transaction, boolean duplicate) {
        LocalDateTime date = transaction.getTransactionDate();
        BudgetCheckResult budgetCheck = evaluateBudget(transaction.getUser(), date.getMonthValue(), date.getYear());
        return new TransactionOutcome(transaction, duplicate, budgetCheck);
    }

    @Override
    public List<Transaction> getAllTransactionsForUser(User user) {
        return transactionRepository.findByUserOrderByTransactionDateDesc(user);
    }

    @Override
    public List<Transaction> getRecentTransactionsForUser(User user, int limit) {
        return transactionRepository.findByUserOrderByTransactionDateDesc(user, PageRequest.of(0, limit));
    }

    @Override
    public BudgetCheckResult checkCurrentMonthBudget(User user) {
        LocalDate today = LocalDate.now();
        return evaluateBudget(user, today.getMonthValue(), today.getYear());
    }

    /**
     * The single place that combines existing Expense records with SUCCESS
     * online transactions and compares the total against the user's Budget
     * for that month - reused by both the transaction-processing flow and
     * the dashboard, so the two can never disagree. Uses the exact same
     * three-tier status PageController.dashboard() already computes
     * (success / warning / danger) so both pieces of UI stay consistent.
     */
    private BudgetCheckResult evaluateBudget(User user, int month, int year) {
        double monthlyExpense = expenseService.getMonthlyExpenseAmount(user, month, year);
        double monthlyTransactions = transactionRepository.sumAmountByUserAndStatusAndMonthAndYear(
                user, TransactionStatus.SUCCESS, month, year);
        double combined = monthlyExpense + monthlyTransactions;

        Optional<Budget> budgetOpt = budgetService.getBudget(user, month, year);
        double budgetAmount = budgetOpt.map(Budget::getAmount).orElse(0.0);
        double remaining = budgetAmount - combined;
        double percentUsed = budgetAmount > 0 ? (combined / budgetAmount) * 100.0 : 0.0;

        String status;
        String message;
        if (budgetOpt.isEmpty()) {
            status = "none";
            message = "No budget set for this month.";
        } else if (combined > budgetAmount) {
            status = "danger";
            double over = combined - budgetAmount;
            message = String.format("Your monthly budget has been exceeded by \u20b9%.2f.", over);
        } else if (percentUsed >= 80) {
            status = "warning";
            message = String.format("Warning: you have used %.0f%% of your monthly budget.", percentUsed);
        } else {
            status = "success";
            message = "Within budget.";
        }

        return new BudgetCheckResult(budgetOpt.isPresent(), budgetAmount, monthlyExpense,
                monthlyTransactions, combined, remaining, percentUsed, status, message);
    }
}
