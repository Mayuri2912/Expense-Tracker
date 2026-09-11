package com.expensetracker.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.expensetracker.dto.AcceptTransactionRequest;
import com.expensetracker.dto.BudgetCheckResult;
import com.expensetracker.dto.CategorySuggestion;
import com.expensetracker.dto.RecurringGroup;
import com.expensetracker.dto.StatementImportResult;
import com.expensetracker.dto.TransactionOutcome;
import com.expensetracker.dto.TransactionReviewView;
import com.expensetracker.dto.TransactionTestRequest;
import com.expensetracker.dto.TransactionWebhookRequest;
import com.expensetracker.entity.Budget;
import com.expensetracker.entity.Category;
import com.expensetracker.entity.CategoryRule;
import com.expensetracker.entity.Expense;
import com.expensetracker.entity.ReviewStatus;
import com.expensetracker.entity.RuleMatchType;
import com.expensetracker.entity.Transaction;
import com.expensetracker.entity.TransactionStatus;
import com.expensetracker.entity.User;
import com.expensetracker.repository.CategoryRuleRepository;
import com.expensetracker.repository.TransactionRepository;
import com.expensetracker.repository.UserRepository;

@Service
public class TransactionServiceImpl implements TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);

    private final TransactionRepository transactionRepository;

    // Injected directly rather than adding a new lookup method to
    // UserService: UserRepository.findByEmail already exists (the login flow
    // already uses it), so resolving the webhook's "userEmail" this way needs
    // no change to UserService/UserServiceImpl at all.
    private final UserRepository userRepository;

    private final ExpenseService expenseService;
    private final BudgetService budgetService;

    // ---- Smart Transaction Review Hub collaborators ----
    private final CategoryService categoryService;
    private final CategoryRuleRepository categoryRuleRepository;
    private final CategorizationService categorizationService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final RecurringDetectionService recurringDetectionService;
    private final StatementImportService statementImportService;

    public TransactionServiceImpl(TransactionRepository transactionRepository,
                                   UserRepository userRepository,
                                   ExpenseService expenseService,
                                   BudgetService budgetService,
                                   CategoryService categoryService,
                                   CategoryRuleRepository categoryRuleRepository,
                                   CategorizationService categorizationService,
                                   DuplicateDetectionService duplicateDetectionService,
                                   RecurringDetectionService recurringDetectionService,
                                   StatementImportService statementImportService) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.expenseService = expenseService;
        this.budgetService = budgetService;
        this.categoryService = categoryService;
        this.categoryRuleRepository = categoryRuleRepository;
        this.categorizationService = categorizationService;
        this.duplicateDetectionService = duplicateDetectionService;
        this.recurringDetectionService = recurringDetectionService;
        this.statementImportService = statementImportService;
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
     * Shared validation + duplicate-protection + categorize + save + budget-
     * check flow used by both the webhook and the test endpoint, once each has
     * already resolved which User the transaction belongs to. The saved
     * transaction always starts life NEEDS_REVIEW.
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
        transaction.setReviewStatus(ReviewStatus.NEEDS_REVIEW);
        applySuggestion(transaction, user);

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

        recomputeRecurringQuietly(user);
        return buildOutcome(transaction, false);
    }

    private void applySuggestion(Transaction transaction, User user) {
        CategorySuggestion suggestion = categorizationService.suggest(
                user, transaction.getMerchant(), transaction.getDescription());
        if (suggestion.isMatched()) {
            transaction.setSuggestedCategory(suggestion.getCategory());
            transaction.setSuggestionReason(suggestion.getReason());
        }
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

    // =====================================================================
    // Smart Transaction Review Hub
    // =====================================================================

    @Override
    public StatementImportResult importStatement(User user, MultipartFile file) {
        return statementImportService.importStatement(user, file);
    }

    @Override
    public List<TransactionReviewView> getReviewInbox(User user) {
        return transactionRepository
                .findByUserAndReviewStatusOrderByTransactionDateDesc(user, ReviewStatus.NEEDS_REVIEW)
                .stream()
                .map(txn -> new TransactionReviewView(
                        txn, duplicateDetectionService.checkAgainstExpenses(user, txn).orElse(null)))
                .toList();
    }

    @Override
    public List<Transaction> getTransactionsByReviewStatus(User user, ReviewStatus status) {
        return transactionRepository.findByUserAndReviewStatusOrderByTransactionDateDesc(user, status);
    }

    @Override
    public long countByReviewStatus(User user, ReviewStatus status) {
        return transactionRepository.countByUserAndReviewStatus(user, status);
    }

    @Override
    public double getPendingReviewAmountThisMonth(User user) {
        LocalDate today = LocalDate.now();
        return transactionRepository.sumAmountByUserAndStatusAndReviewStatusAndMonthAndYear(
                user, TransactionStatus.SUCCESS, ReviewStatus.NEEDS_REVIEW,
                today.getMonthValue(), today.getYear());
    }

    @Override
    public List<RecurringGroup> getRecurringGroups(User user) {
        return recurringDetectionService.summarizeForUser(user);
    }

    @Override
    @Transactional
    public Transaction acceptTransaction(Long transactionId, User user, AcceptTransactionRequest request) {
        Transaction txn = requireOwnedTransaction(transactionId, user);
        if (txn.getReviewStatus() == ReviewStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This transaction has already been accepted into your expenses.");
        }
        if (request == null || request.getCategoryId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a category to file this under.");
        }

        // Re-fetched server-side so a tampered categoryId can never attach
        // another user's category (same guard the expense form uses).
        Category category = categoryService.getCategoryByIdForUser(request.getCategoryId(), user);

        Expense expense = new Expense();
        expense.setTitle(firstNonBlank(request.getTitle(), txn.getMerchant(), "Online transaction"));
        expense.setAmount(request.getAmount() != null && request.getAmount() > 0 ? request.getAmount() : txn.getAmount());
        expense.setExpenseDate(request.getExpenseDate() != null
                ? request.getExpenseDate() : txn.getTransactionDate().toLocalDate());
        expense.setPaymentMethod(firstNonBlank(request.getPaymentMethod(), txn.getPaymentMethod(), null));
        expense.setDescription(buildExpenseDescription(txn));
        expense.setCategory(category);

        Expense saved = expenseService.addExpense(expense, user);

        txn.setExpense(saved);
        txn.setReviewStatus(ReviewStatus.ACCEPTED);
        transactionRepository.save(txn);

        if (request.isRememberRule()) {
            rememberRule(user, txn.getMerchant(), category);
        }
        recomputeRecurringQuietly(user);

        log.info("User {} accepted transaction {} -> expense {}", user.getId(),
                txn.getExternalTransactionId(), saved.getId());
        return txn;
    }

    @Override
    @Transactional
    public Transaction ignoreTransaction(Long transactionId, User user) {
        Transaction txn = requireOwnedTransaction(transactionId, user);
        if (txn.getReviewStatus() == ReviewStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This transaction is already an expense. Delete that expense instead if it was a mistake.");
        }
        txn.setReviewStatus(ReviewStatus.IGNORED);
        transactionRepository.save(txn);
        recomputeRecurringQuietly(user);
        return txn;
    }

    @Override
    @Transactional
    public Transaction moveBackToReview(Long transactionId, User user) {
        Transaction txn = requireOwnedTransaction(transactionId, user);
        if (txn.getReviewStatus() == ReviewStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This transaction is already an expense and can't be moved back.");
        }
        txn.setReviewStatus(ReviewStatus.NEEDS_REVIEW);
        transactionRepository.save(txn);
        recomputeRecurringQuietly(user);
        return txn;
    }

    // ---- helpers --------------------------------------------------------

    private Transaction requireOwnedTransaction(Long id, User user) {
        return transactionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
    }

    private String buildExpenseDescription(Transaction txn) {
        String base = "From online transaction " + txn.getExternalTransactionId();
        if (txn.getDescription() != null && !txn.getDescription().isBlank()) {
            base = base + " — " + txn.getDescription();
        }
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    /** Save a personal "merchant text contains X -> this category" rule, unless an equivalent one already exists. */
    private void rememberRule(User user, String merchant, Category category) {
        if (merchant == null || merchant.isBlank()) {
            return;
        }
        String pattern = merchant.trim().toLowerCase();
        if (pattern.length() > 100) {
            pattern = pattern.substring(0, 100);
        }
        final String finalPattern = pattern;

        boolean exists = categoryRuleRepository.findByUserOrderByPriorityAscIdAsc(user).stream()
                .anyMatch(r -> finalPattern.equalsIgnoreCase(r.getPattern())
                        && category.getName().equalsIgnoreCase(r.getCategoryName()));
        if (exists) {
            return;
        }

        CategoryRule rule = new CategoryRule();
        rule.setUser(user);
        rule.setMatchType(RuleMatchType.CONTAINS);
        rule.setPattern(finalPattern);
        rule.setCategoryName(category.getName());
        rule.setCategory(category);
        rule.setPriority(50); // personal rules sit ahead of the priority-100 defaults
        rule.setActive(true);
        categoryRuleRepository.save(rule);
        log.info("User {} saved personal categorization rule '{}' -> {}", user.getId(), finalPattern, category.getName());
    }

    private void recomputeRecurringQuietly(User user) {
        try {
            recurringDetectionService.recomputeForUser(user);
        } catch (RuntimeException ex) {
            // Non-critical: a failure here must never break ingestion or an accept/ignore.
            log.warn("Recurring recompute for user {} failed: {}", user.getId(), ex.toString());
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /**
     * The single place that combines existing Expense records with SUCCESS
     * online transactions and compares the total against the user's Budget
     * for that month - used by the webhook/test response. (The dashboard's
     * primary budget figure now counts confirmed expenses only; see
     * PageController.)
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
            message = String.format("Your monthly budget has been exceeded by ₹%.2f.", over);
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
